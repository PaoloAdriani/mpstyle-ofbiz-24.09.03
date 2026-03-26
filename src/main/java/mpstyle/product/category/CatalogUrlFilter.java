package mpstyle.product.category;

import org.apache.logging.log4j.ThreadContext;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.UrlServletHelper;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.product.category.CatalogUrlServlet;
import org.apache.ofbiz.product.category.CategoryContentWrapper;
import org.apache.ofbiz.product.category.CategoryWorker;
import org.apache.ofbiz.product.product.ProductContentWrapper;
import org.apache.ofbiz.webapp.WebAppUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CatalogUrlFilter implements Filter {

    private static final String MODULE = org.apache.ofbiz.product.category.CatalogUrlFilter.class.getName();
    private static final String PRODUCT_REQUEST = "product";
    private static final String CATEGORY_REQUEST = "category";
    private static String defaultLocaleString;
    private static String redirectUrl;

    private FilterConfig config;

    /**
     * Gets config.
     * @return the config
     */
    public FilterConfig getConfig() {
        return config;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.config = filterConfig;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        Delegator delegator = (Delegator) httpRequest.getSession().getServletContext().getAttribute("delegator");

        // set initial parameters
        String initDefaultLocalesString = config.getInitParameter("defaultLocaleString");
        String initRedirectUrl = config.getInitParameter("redirectUrl");
        setDefaultLocaleString(UtilValidate.isNotEmpty(initDefaultLocalesString) ? initDefaultLocalesString : "");
        setRedirectUrl(UtilValidate.isNotEmpty(initRedirectUrl) ? initRedirectUrl : "");
        String pathInfo = httpRequest.getServletPath();
        if (UtilValidate.isNotEmpty(pathInfo)) {
            List<String> pathElements = StringUtil.split(pathInfo, "/");
            String alternativeUrl = pathElements.get(0);

            String productId = null;
            String productCategoryId = null;
            String urlContentId = null;
            String contextPath = httpRequest.getContextPath();
            contextPath = contextPath.endsWith("/") ? contextPath : contextPath + "/";

            try {
                // look for productId
                if (alternativeUrl.endsWith("-p")) {

                    /////// PHASE 1 - SEARCH: reverse lookup
                    //remove the suffix -p first
                    String alternativeUrlStr = alternativeUrl.substring(0, alternativeUrl.length() - 2);
                    List<String> alternativeUrlElements = StringUtil.split(alternativeUrlStr, "-");
                    //Reverse to have the hottest substring first
                    Collections.reverse(alternativeUrlElements);
                    String prevCandidate = "";
                    for (String candidateProductId: alternativeUrlElements) {
                        //found the product id in previous iterations, break the loop and go to forward URL generation
                        if(UtilValidate.isNotEmpty(productId)) {
                            break;
                        }
                        candidateProductId = candidateProductId  + (prevCandidate.trim().isEmpty() ? "" : "-") + prevCandidate;
                        List<GenericValue> productContentList = EntityQuery.use(delegator).
                                from("ProductContentAndInfo").
                                where("productId", candidateProductId, "productContentTypeId", "ALTERNATIVE_URL")
                                .cache(true).filterByDate().queryList();
                        for (GenericValue pc : productContentList) {
                            GenericValue eText = pc.getRelatedOne("ElectronicText", true);
                            if(eText != null) {
                                String textData = (String) eText.get("textData");
                                textData = UrlServletHelper.invalidCharacter(textData);
                                if (alternativeUrlStr.equals(textData + "-" + candidateProductId)) {
                                    productId = candidateProductId;
                                    break;
                                }
                            }
                        }
                        prevCandidate = candidateProductId;
                    }

                    if (UtilValidate.isEmpty(productId)) {
                        /////// PHASE 2 - SEARCH: reverse lookup and making the product url with each substring
                        // to see if it matches the alternative URL
                        prevCandidate = "";
                        for (String candidateProductId: alternativeUrlElements) {
                            //found the product id in previous iterations, break the loop and go to forward URL generation
                            if(UtilValidate.isNotEmpty(productId)) {
                                break;
                            }
                            candidateProductId = candidateProductId  + (prevCandidate.trim().isEmpty() ? "" : "-") + prevCandidate;
                            EntityCondition likeCond = EntityCondition.makeCondition("productId", EntityOperator.LIKE, candidateProductId+"%");
                            List<GenericValue> productCandidateList = EntityQuery.use(delegator).
                                    from("Product").
                                    where(likeCond).cache(true).queryList();

                            if (UtilValidate.isEmpty(productCandidateList)) {
                                prevCandidate = candidateProductId;
                                continue;
                            }

                            for(GenericValue productCandidate : productCandidateList) {
                                String generatedProductUrl = CatalogUrlFilter.makeProductUrl(httpRequest, null, null, productCandidate.getString("productId"));
                                if (generatedProductUrl.endsWith(contextPath + alternativeUrl)) {
                                    productId = candidateProductId;
                                    break;
                                }
                            }
                            prevCandidate = candidateProductId;
                        }
                    }

                    //////// PHASE 3 - OOTB SEARCH with enhancements (substring instead of replace to avoid replacing
                    // multiple times if textData appears multiple times in the alternativeUrl)
                    if (UtilValidate.isEmpty(productId)) {
                        List<EntityCondition> productContentConds = new LinkedList<>();
                        productContentConds.add(EntityCondition.makeCondition("productContentTypeId", "ALTERNATIVE_URL"));
                        productContentConds.add(EntityUtil.getFilterByDateExpr());
                        List<GenericValue> productContentInfos = EntityQuery.use(delegator).from("ProductContentAndInfo").where(productContentConds)
                                .orderBy("-fromDate").cache(true).queryList();
                        if (UtilValidate.isNotEmpty(productContentInfos)) {
                            for (GenericValue productContentInfo : productContentInfos) {
                                // productId found in previous loop: quit the loop and go to forward URL generation
                                if (UtilValidate.isNotEmpty(productId)) {
                                    break;
                                }
                                String contentId = (String) productContentInfo.get("contentId");
                                List<GenericValue> contentAssocDataResourceViewTos = EntityQuery.use(delegator).from("ContentAssocDataResourceViewTo")
                                        .where("contentIdStart", contentId, "caContentAssocTypeId", "ALTERNATE_LOCALE", "drDataResourceTypeId",
                                                "ELECTRONIC_TEXT").cache(true).queryList();
                                if (UtilValidate.isNotEmpty(contentAssocDataResourceViewTos)) {
                                    for (GenericValue contentAssocDataResourceViewTo : contentAssocDataResourceViewTos) {
                                        GenericValue electronicText = contentAssocDataResourceViewTo.getRelatedOne("ElectronicText", true);
                                        if (electronicText != null) {
                                            String textData = (String) electronicText.get("textData");
                                            textData = UrlServletHelper.invalidCharacter(textData);
                                            if (alternativeUrl.matches(textData + ".+$")) {
                                                String productIdStr = null;
                                                productIdStr = alternativeUrl.substring(textData.length() + 1);
                                                productIdStr = productIdStr.substring(0, productIdStr.length() - 2);
                                                String checkProductId = (String) productContentInfo.get("productId");
                                                if (productIdStr.equalsIgnoreCase(checkProductId)) {
                                                    productId = checkProductId;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                if (UtilValidate.isEmpty(productId)) {
                                    List<GenericValue> contentDataResourceViews = EntityQuery.use(delegator).from("ContentDataResourceView")
                                            .where("contentId", contentId, "drDataResourceTypeId", "ELECTRONIC_TEXT").cache(true).queryList();
                                    for (GenericValue contentDataResourceView : contentDataResourceViews) {
                                        GenericValue electronicText = contentDataResourceView.getRelatedOne("ElectronicText", true);
                                        if (UtilValidate.isNotEmpty(electronicText)) {
                                            String textData = (String) electronicText.get("textData");
                                            if (UtilValidate.isNotEmpty(textData)) {
                                                textData = UrlServletHelper.invalidCharacter(textData);
                                                if (alternativeUrl.matches(textData + ".+$")) {
                                                    String productIdStr = null;
                                                    //productIdStr = alternativeUrl.replace(textData + "-", "");
                                                    productIdStr = alternativeUrl.substring(textData.length() + 1);
                                                    //productIdStr = productIdStr.replace("-p", "");
                                                    productIdStr = productIdStr.substring(0, productIdStr.length() - 2);
                                                    String checkProductId = (String) productContentInfo.get("productId");
                                                    if (productIdStr.equalsIgnoreCase(checkProductId)) {
                                                        productId = checkProductId;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // look for productCategoryId
                if (alternativeUrl.endsWith("-c")) {
                    /////////// PHASE 1 - SEARCH: reverse lookup
                    //remove the suffix -c first
                    String alternativeUrlStr = alternativeUrl.substring(0, alternativeUrl.length() - 2);

                    List<String> alternativeUrlElements = StringUtil.split(alternativeUrlStr, "-");

                    //Reverse to have the hottest substring first
                    Collections.reverse(alternativeUrlElements);

                    String prevCandidate = "";
                    for (String candidateCatId: alternativeUrlElements) {
                        //found the category id in previous iterations, break the loop and go to forward URL generation
                        if(UtilValidate.isNotEmpty(productCategoryId)) {
                            break;
                        }

                        candidateCatId = candidateCatId  + (prevCandidate.trim().isEmpty() ? "" : "-") + prevCandidate;

                        List<GenericValue> productCategoryContentList = EntityQuery.use(delegator).
                                                                    from("ProductCategoryContentAndInfo").
                                                                    where("productCategoryId", candidateCatId, "prodCatContentTypeId", "ALTERNATIVE_URL")
                                                                    .cache(true).filterByDate().queryList();

                        for (GenericValue pcc : productCategoryContentList) {
                            GenericValue eText = pcc.getRelatedOne("ElectronicText", true);
                            if(eText != null) {
                                String textData = (String) eText.get("textData");
                                textData = UrlServletHelper.invalidCharacter(textData);
                                if (alternativeUrlStr.equals(textData + "-" + candidateCatId)) {
                                    productCategoryId = candidateCatId;
                                    break;
                                }
                            }
                        }
                        prevCandidate = candidateCatId;
                    }
                    ////////// END PHASE 1 - SEARCH

                    //////////// PHASE 2 - SEARCH: reverse lookup and making the category url with each substring
                    // to see if it matches the alternative URL
                    if (UtilValidate.isEmpty(productCategoryId)) {
                        prevCandidate = "";
                        for (String candidateCatId: alternativeUrlElements) {
                            //found the category id in previous iterations, break the loop and go to forward URL generation
                            if(UtilValidate.isNotEmpty(productCategoryId)) {
                                break;
                            }
                            candidateCatId = candidateCatId  + (prevCandidate.trim().isEmpty() ? "" : "-") + prevCandidate;

                            EntityCondition likeCond = EntityCondition.makeCondition("productCategoryId", EntityOperator.LIKE, candidateCatId+"%");
                            List<GenericValue> productCategoryCandidateList = EntityQuery.use(delegator).
                                    from("ProductCategory").
                                    where(likeCond).cache(true).queryList();

                            if (UtilValidate.isEmpty(productCategoryCandidateList)) {
                                prevCandidate = candidateCatId;
                                continue;
                            }

                            for(GenericValue productCategoryCandidate : productCategoryCandidateList) {
                                String generatedCategoryUrl = CatalogUrlFilter.makeCategoryUrl(httpRequest, null, productCategoryCandidate.getString("productCategoryId"), null, null, null, null, null);
                                if (generatedCategoryUrl.endsWith(contextPath + alternativeUrl)) {
                                    productCategoryId = candidateCatId;
                                    break;
                                }
                            }
                            prevCandidate = candidateCatId;
                        }
                    }

                    /////////// PHASE 3 - OOTB SEARCH with enhancements (substring instead of replace to avoid
                    // replacing multiple times if textData appears multiple times in the alternativeUrl)
                    if (UtilValidate.isEmpty(productCategoryId)) {
                        List<EntityCondition> productCategoryContentConds = new LinkedList<>();
                        productCategoryContentConds.add(EntityCondition.makeCondition("prodCatContentTypeId", "ALTERNATIVE_URL"));
                        productCategoryContentConds.add(EntityUtil.getFilterByDateExpr());
                        List<GenericValue> productCategoryContentInfos = EntityQuery.use(delegator).from("ProductCategoryContentAndInfo")
                                .where(productCategoryContentConds).orderBy("-fromDate").cache(true).queryList();
                        if (UtilValidate.isNotEmpty(productCategoryContentInfos)) {
                            for (GenericValue productCategoryContentInfo : productCategoryContentInfos) {
                                // productCategoryId found in previous loop: quit the loop and go to forward URL generation
                                if (UtilValidate.isNotEmpty(productCategoryId)) {
                                    break;
                                }
                                String contentId = (String) productCategoryContentInfo.get("contentId");
                                List<GenericValue> contentAssocDataResourceViewTos = EntityQuery.use(delegator).from("ContentAssocDataResourceViewTo")
                                        .where("contentIdStart", contentId, "caContentAssocTypeId", "ALTERNATE_LOCALE", "drDataResourceTypeId",
                                                "ELECTRONIC_TEXT").cache(true).queryList();
                                if (UtilValidate.isNotEmpty(contentAssocDataResourceViewTos)) {
                                    for (GenericValue contentAssocDataResourceViewTo : contentAssocDataResourceViewTos) {
                                        GenericValue electronicText = contentAssocDataResourceViewTo.getRelatedOne("ElectronicText", true);
                                        if (electronicText != null) {
                                            String textData = (String) electronicText.get("textData");
                                            if (UtilValidate.isNotEmpty(textData)) {
                                                textData = UrlServletHelper.invalidCharacter(textData);
                                                if (alternativeUrl.matches(textData + ".+$")) {
                                                    String productCategoryStr = null;
                                                    productCategoryStr = alternativeUrl.substring(textData.length() + 1);
                                                    productCategoryStr = productCategoryStr.substring(0, productCategoryStr.length() - 2);
                                                    String checkProductCategoryId = (String) productCategoryContentInfo.get("productCategoryId");
                                                    if (productCategoryStr.equalsIgnoreCase(checkProductCategoryId)) {
                                                        productCategoryId = checkProductCategoryId;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (UtilValidate.isEmpty(productCategoryId)) {
                                    List<GenericValue> contentDataResourceViews = EntityQuery.use(delegator).from("ContentDataResourceView")
                                            .where("contentId", contentId, "drDataResourceTypeId", "ELECTRONIC_TEXT").cache(true).queryList();
                                    for (GenericValue contentDataResourceView : contentDataResourceViews) {
                                        GenericValue electronicText = contentDataResourceView.getRelatedOne("ElectronicText", true);
                                        if (electronicText != null) {
                                            String textData = (String) electronicText.get("textData");
                                            if (UtilValidate.isNotEmpty(textData)) {
                                                textData = UrlServletHelper.invalidCharacter(textData);
                                                if (alternativeUrl.matches(textData + ".+$")) {
                                                    String productCategoryStr = null;
                                                    productCategoryStr = alternativeUrl.substring(textData.length() + 1);
                                                    productCategoryStr = productCategoryStr.substring(0, productCategoryStr.length() - 2);
                                                    String checkProductCategoryId = (String) productCategoryContentInfo.get("productCategoryId");
                                                    if (productCategoryStr.equalsIgnoreCase(checkProductCategoryId)) {
                                                        productCategoryId = checkProductCategoryId;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ////////////END OOTB SEARCH
                }

            } catch (GenericEntityException e) {
                Debug.logWarning("Cannot look for product and product category", MODULE);
            }

            // generate forward URL
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("/" + WebAppUtil.CONTROL_MOUNT_POINT);
            if (UtilValidate.isNotEmpty(productId)) {
                try {
                    List<EntityCondition> conds = new LinkedList<>();
                    conds.add(EntityCondition.makeCondition("productId", productId));
                    conds.add(EntityUtil.getFilterByDateExpr());
                    List<GenericValue> productCategoryMembers = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategoryMember")
                            .where(conds).orderBy("-fromDate").cache(true).queryList();
                    if (UtilValidate.isNotEmpty(productCategoryMembers)) {
                        GenericValue productCategoryMember = EntityUtil.getFirst(productCategoryMembers);
                        productCategoryId = productCategoryMember.getString("productCategoryId");
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Cannot find product category for product: " + productId, MODULE);
                }
                urlBuilder.append("/" + PRODUCT_REQUEST);
            } else {
                urlBuilder.append("/" + CATEGORY_REQUEST);
            }

            // generate trail belong to a top category
            String topCategoryId = CategoryWorker.getCatalogTopCategory(httpRequest, null);
            List<GenericValue> trailCategories = CategoryWorker.getRelatedCategoriesRet(httpRequest, "trailCategories",
                    topCategoryId, false, false, true);
            List<String> trailCategoryIds = EntityUtil.getFieldListFromEntityList(trailCategories, "productCategoryId", true);

            // look for productCategoryId from productId
            if (UtilValidate.isNotEmpty(productId)) {
                try {
                    List<EntityCondition> rolllupConds = new LinkedList<>();
                    rolllupConds.add(EntityCondition.makeCondition("productId", productId));
                    rolllupConds.add(EntityUtil.getFilterByDateExpr());
                    List<GenericValue> productCategoryMembers = EntityQuery.use(delegator).from("ProductCategoryMember").where(rolllupConds)
                            .orderBy("-fromDate").cache(true).queryList();
                    for (GenericValue productCategoryMember : productCategoryMembers) {
                        String trailCategoryId = productCategoryMember.getString("productCategoryId");
                        if (trailCategoryIds.contains(trailCategoryId)) {
                            productCategoryId = trailCategoryId;
                            break;
                        }
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Cannot generate trail from product category", MODULE);
                }
            }

            // generate trail elements from productCategoryId
            if (UtilValidate.isNotEmpty(productCategoryId)) {
                List<String> trailElements = new LinkedList<>();
                trailElements.add(productCategoryId);
                String parentProductCategoryId = productCategoryId;
                while (UtilValidate.isNotEmpty(parentProductCategoryId)) {
                    // find product category rollup
                    try {
                        List<EntityCondition> rolllupConds = new LinkedList<>();
                        rolllupConds.add(EntityCondition.makeCondition("productCategoryId", parentProductCategoryId));
                        rolllupConds.add(EntityUtil.getFilterByDateExpr());
                        List<GenericValue> productCategoryRollups = EntityQuery.use(delegator).from("ProductCategoryRollup").where(rolllupConds)
                                .orderBy("-fromDate").cache(true).queryList();
                        if (UtilValidate.isNotEmpty(productCategoryRollups)) {
                            // add only categories that belong to the top category to trail
                            for (GenericValue productCategoryRollup : productCategoryRollups) {
                                String trailCategoryId = productCategoryRollup.getString("parentProductCategoryId");
                                parentProductCategoryId = trailCategoryId;
                                if (trailCategoryIds.contains(trailCategoryId)) {
                                    trailElements.add(trailCategoryId);
                                    break;
                                }
                            }
                        } else {
                            parentProductCategoryId = null;
                        }
                    } catch (GenericEntityException e) {
                        Debug.logError(e, "Cannot generate trail from product category", MODULE);
                    }
                }
                Collections.reverse(trailElements);

                List<String> trail = CategoryWorker.getTrail(httpRequest);
                if (trail == null) {
                    trail = new LinkedList<>();
                }

                // adjust trail
                String previousCategoryId = null;
                if (!trail.isEmpty()) {
                    previousCategoryId = trail.get(trail.size() - 1);
                }
                trail = CategoryWorker.adjustTrail(trail, productCategoryId, previousCategoryId);

                if (trailElements.size() == 1) {
                    CategoryWorker.setTrail(request, trailElements.get(0), null);
                } else if (trailElements.size() == 2) {
                    CategoryWorker.setTrail(request, trailElements.get(1), trailElements.get(0));
                } else if (trailElements.size() > 2) {
                    if (trail.contains(trailElements.get(0))) {
                        // first category is in the trail, so remove it everything after that and fill it in with the list from the pathInfo
                        int firstElementIndex = trail.indexOf(trailElements.get(0));
                        while (trail.size() > firstElementIndex) {
                            trail.remove(firstElementIndex);
                        }
                        trail.addAll(trailElements);
                    } else {
                        // first category is NOT in the trail, so clear out the trail and use the trailElements list
                        trail.clear();
                        trail.addAll(trailElements);
                    }
                    CategoryWorker.setTrail(request, trail);
                }

                request.setAttribute("productCategoryId", productCategoryId);

                if (productId != null) {
                    request.setAttribute("product_id", productId);
                    request.setAttribute("productId", productId);
                }
            }
            //Set view query parameters
            UrlServletHelper.setViewQueryParameters(request, urlBuilder);
            if (UtilValidate.isNotEmpty(productId) || UtilValidate.isNotEmpty(productCategoryId) || UtilValidate.isNotEmpty(urlContentId)) {
                Debug.logInfo("[Filtered request]: " + pathInfo + " (" + urlBuilder + ")", MODULE);
                RequestDispatcher dispatch = request.getRequestDispatcher(urlBuilder.toString());
                dispatch.forward(request, response);
                return;
            }
            //Check path alias
            UrlServletHelper.checkPathAlias(request, httpResponse, delegator, pathInfo);
        }
        try {
            String uriWithContext = httpRequest.getRequestURI();
            String context = httpRequest.getContextPath();
            String uri = uriWithContext.substring(context.length());
            // support OFBizDynamicThresholdFilter in log4j2.xml
            ThreadContext.put("uri", uri);

            // we're done checking; continue on
            chain.doFilter(request, response);
        } finally {
            ThreadContext.remove("uri");
        }
    }

    @Override
    public void destroy() {

    }

    public static String makeCategoryUrl(HttpServletRequest request, String previousCategoryId,
                                         String productCategoryId, String productId, String viewSize, String viewIndex, String viewSort,
                                         String searchString) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        try {
            GenericValue productCategory = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId", productCategoryId)
                    .cache().queryOne();
            CategoryContentWrapper wrapper = new CategoryContentWrapper(productCategory, request);
            List<String> trail = CategoryWorker.getTrail(request);
            return makeCategoryUrl(delegator, wrapper, trail, request.getContextPath(), previousCategoryId, productCategoryId, productId,
                    viewSize, viewIndex, viewSort, searchString);
        } catch (GenericEntityException e) {
            Debug.logWarning(e, "Cannot create category's URL for: " + productCategoryId, MODULE);
            return redirectUrl;
        }
    }

    public static String makeCategoryUrl(Delegator delegator, CategoryContentWrapper wrapper, List<String> trail, String contextPath,
                                         String previousCategoryId, String productCategoryId, String productId, String viewSize, String viewIndex, String viewSort,
                                         String searchString) {
        String url = "";
        StringUtil.StringWrapper alternativeUrl = wrapper.get("ALTERNATIVE_URL", "url");

        if (UtilValidate.isNotEmpty(alternativeUrl) && UtilValidate.isNotEmpty(alternativeUrl.toString())) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(contextPath);
            if (urlBuilder.length() == 0 || urlBuilder.charAt(urlBuilder.length() - 1) != '/') {
                urlBuilder.append("/");
            }
            // append alternative URL
            url = UrlServletHelper.invalidCharacter(alternativeUrl.toString());
            urlBuilder.append(url);
            if (UtilValidate.isNotEmpty(productCategoryId)) {
                urlBuilder.append("-");
                urlBuilder.append(productCategoryId);
                urlBuilder.append("-c");
            }
            // append view index
            if (UtilValidate.isNotEmpty(viewIndex)) {
                if (!urlBuilder.toString().endsWith("?") && !urlBuilder.toString().endsWith("&")) {
                    urlBuilder.append("?");
                }
                urlBuilder.append("viewIndex=" + viewIndex + "&");
            }
            // append view size
            if (UtilValidate.isNotEmpty(viewSize)) {
                if (!urlBuilder.toString().endsWith("?") && !urlBuilder.toString().endsWith("&")) {
                    urlBuilder.append("?");
                }
                urlBuilder.append("viewSize=" + viewSize + "&");
            }
            // append view sort
            if (UtilValidate.isNotEmpty(viewSort)) {
                if (!urlBuilder.toString().endsWith("?") && !urlBuilder.toString().endsWith("&")) {
                    urlBuilder.append("?");
                }
                urlBuilder.append("viewSort=" + viewSort + "&");
            }
            // append search string
            if (UtilValidate.isNotEmpty(searchString)) {
                if (!urlBuilder.toString().endsWith("?") && !urlBuilder.toString().endsWith("&")) {
                    urlBuilder.append("?");
                }
                urlBuilder.append("searchString=" + searchString + "&");
            }
            if (urlBuilder.toString().endsWith("&")) {
                return urlBuilder.toString().substring(0, urlBuilder.toString().length() - 1);
            }

            url = urlBuilder.toString();
        } else {
            if (UtilValidate.isEmpty(trail)) {
                trail = new LinkedList<>();
            }
            url = CatalogUrlServlet.makeCatalogUrl(contextPath, trail, productId, productCategoryId, previousCategoryId);
        }
        return url;
    }
    public static String makeProductUrl(HttpServletRequest request, String previousCategoryId, String productCategoryId, String productId) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String url = null;
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
            ProductContentWrapper wrapper = new ProductContentWrapper(product, request);
            List<String> trail = CategoryWorker.getTrail(request);
            url = makeProductUrl(wrapper, trail, request.getContextPath(), previousCategoryId, productCategoryId, productId);
        } catch (GenericEntityException e) {
            Debug.logWarning(e, "Cannot create product's URL for: " + productId, MODULE);
            return redirectUrl;
        }
        return url;
    }

    public static String makeProductUrl(ProductContentWrapper wrapper, List<String> trail, String contextPath, String previousCategoryId,
                                        String productCategoryId, String productId) {
        String url = "";
        StringUtil.StringWrapper alternativeUrl = wrapper.get("ALTERNATIVE_URL", "url");
        if (UtilValidate.isNotEmpty(alternativeUrl) && UtilValidate.isNotEmpty(alternativeUrl.toString())) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(contextPath);
            if (urlBuilder.length() == 0 || urlBuilder.charAt(urlBuilder.length() - 1) != '/') {
                urlBuilder.append("/");
            }
            // append alternative URL
            url = UrlServletHelper.invalidCharacter(alternativeUrl.toString());
            urlBuilder.append(url);
            if (UtilValidate.isNotEmpty(productId)) {
                urlBuilder.append("-");
                urlBuilder.append(productId);
                urlBuilder.append("-p");
            }
            url = urlBuilder.toString();
        } else {
            if (UtilValidate.isEmpty(trail)) {
                trail = new LinkedList<>();
            }
            url = CatalogUrlServlet.makeCatalogUrl(contextPath, trail, productId, productCategoryId, previousCategoryId);
        }
        return url;
    }

    public static String getDefaultLocaleString() {
        return defaultLocaleString;
    }

    public static void setDefaultLocaleString(String defaultLocaleString) {
        mpstyle.product.category.CatalogUrlFilter.defaultLocaleString = defaultLocaleString;
    }

    public static String getRedirectUrl() {
        return redirectUrl;
    }

    public static void setRedirectUrl(String redirectUrl) {
        mpstyle.product.category.CatalogUrlFilter.redirectUrl = redirectUrl;
    }

}
