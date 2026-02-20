/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.feed.channable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.*;

import java.util.Map.Entry;

import mpstyle.log.FeedLogger;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.xml.XMLUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.UrlServletHelper;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.product.category.CategoryContentWrapper;
import org.apache.ofbiz.product.product.ProductContentWrapper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author equake58
 */
public class ChannableServices {
    
    public static final String module = ChannableServices.class.getName();
    
    private static String systemResourceId = "channable";
    
    private final static String DEFAULT_LOCALE = "it_IT";
    private final static String DEFAULT_CURRENCY_UOM = "EUR";
    private final static String DEFAULT_COUNTRY_GEO = "ITA";
    private final static String IN_STOCK_STR = "in stock";
    private final static String OUTOF_STOCK_STR = "out of stock";
    private final static String EAN_GOOD_IDENT_TYPE = "EAN";
    private final static String PRODUCT_NEW_CONDITION = "New";
    private final static String PRODUCT_GENDER_F = "Female";
    
    /**
     * 
     * @param dctx
     * @param context
     * @return 
     */
    public static Map<String, Object> createChannableXMLFeed(DispatchContext dctx, Map<String, Object> context) {
        
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        
        final String method = "createChannableXMLFeed";
     
        String logfilename = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.logfilename", delegator);
        String logdirpath = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.logdirpath", delegator);
        String feedFileOutputDir = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.feedOutPath", delegator);
        String feedFileName = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.feedFilename", delegator);
        String siteBaseUrl = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.siteBaseUrl", delegator);
        String itaWebapp = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.webapp.ita", delegator);
        String catalogPerVariant = EntityUtilProperties.getPropertyValue(systemResourceId, "channbale.feed.per.variant", delegator);
        
        
        /* Reading service context parameters */
        String loginUsername = (String) context.get("username");
        String loginPassword = (String) context.get("password");
        String channableCatalogId = (String) context.get("channableCatalogId");
        String channableCategoryId = (String) context.get("channableCategoryId");
        Boolean exlcudeOutOfSupportProducts = (Boolean) context.get("exlcudeOutOfSupportProducts");
        String brand = (String) context.get("brand");
        String productStoreId = (String) context.get("productStoreId");
        String googleProductCategoryId = (String) context.get("googleProductCategoryId");
        String requireInventory = (String) context.get("requireInventory");
        
        
        String feedFileOutput = "";
        boolean useVariantProduct = false;
        
        if(catalogPerVariant != null && "Y".equals(catalogPerVariant)) {
            useVariantProduct = true;
        }

             
        if(!feedFileOutputDir.endsWith(File.separator)) {
            feedFileOutput = feedFileOutputDir + File.separator + feedFileName;
        }else{
            feedFileOutput = feedFileOutputDir + feedFileName;
        }
        
        Locale feedLocale = null;
        
        Debug.logWarning("* loginUsername: "+loginUsername, module);
        Debug.logWarning("* loginPassword: "+loginPassword, module);
        Debug.logWarning("* channableCatalogId: "+channableCatalogId, module);
        Debug.logWarning("* channableCategoryId: "+channableCategoryId, module);
        Debug.logWarning("* feedFileName: "+feedFileName, module);
        Debug.logWarning("* feedFileOutputDir: "+feedFileOutputDir, module);
        Debug.logWarning("* feedFileOutput: "+feedFileOutput, module);
        Debug.logWarning("* exlcudeOutOfSupportProducts: "+exlcudeOutOfSupportProducts, module);
        Debug.logWarning("* brand: "+brand, module);
        Debug.logWarning("* productStoreId: "+productStoreId, module);
        Debug.logWarning("* googleProductCategoryId: "+googleProductCategoryId, module);
        Debug.logWarning("* requireInventory: "+requireInventory, module);
        
        FeedLogger logger = new FeedLogger(delegator.getDelegatorTenantId(), logfilename, logdirpath);
        
        logger.logInfo("******** START (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
        
        
        //Check existance of channable catalog, channable product category, product store
        GenericValue channableProdCatalog = null;
        GenericValue channableProdCategory = null;
        GenericValue productStore = null;
        List<GenericValue> feedProductList = null;
        Map<String, Object> channOutMap = null;
        int feedProductCount = 0;
        
        boolean useSingleCategory = false; //used for product retrieval
        
        try {
            channableProdCatalog = delegator.findOne("ProdCatalog", UtilMisc.toMap("prodCatalogId", channableCatalogId), false);
        }catch(GenericEntityException gee) {
            String msg = "Error in retrieving ProdCatalog with id ["+channableCatalogId+"]. Quit.";
            logger.logError(msg);
            Debug.logError(gee, msg, module);
            return ServiceUtil.returnError(msg);
        }
        
        if(channableProdCatalog == null) {
            String msg = "ProdCatalog with id ["+channableCatalogId+"] does not exists. Quit.";
            logger.logError(msg);
            return ServiceUtil.returnError(msg);
        }
        
        
        if(UtilValidate.isNotEmpty(channableCategoryId)) {
            
            try {
                channableProdCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", channableCategoryId), false);
            }catch(GenericEntityException gee) {
                String msg = "Error in retrieving ProductCategory with id ["+channableCategoryId+"]. Quit.";
                logger.logError(msg);
                Debug.logError(gee, msg, module);
                ServiceUtil.returnError(msg);
            }
            
            if(channableProdCategory == null) {
                String msg = "ProductCategory with id ["+channableCategoryId+"] does not exists. Quit.";
                logger.logError(msg);
                return ServiceUtil.returnError(msg);
            }
            
            useSingleCategory = true;
            
        }
        
        try {
            productStore = delegator.findOne("ProductStore", UtilMisc.toMap("productStoreId", productStoreId), false);
        }catch(GenericEntityException gee) {
            String msg = "Error in retrieving ProductStore with id ["+productStoreId+"]. Quit.";
            logger.logError(msg);
            Debug.logError(gee, msg, module);
            return ServiceUtil.returnError(msg);
        }
        
        if(productStore == null) {
            String msg = "ProductStore with id ["+productStoreId+"] does not exists. Quit.";
            logger.logError(msg);
            return ServiceUtil.returnError(msg);
        }
        
        String productStoreLocale = (String) productStore.get("defaultLocaleString");
        if(productStoreLocale == null) {
            productStoreLocale = DEFAULT_LOCALE;
        }
        
        feedLocale = UtilMisc.parseLocale(productStoreLocale);
        
        String storeCurrencyUomId = (String) productStore.get("defaultCurrencyUomId");
        
        //set a default currency
        if(storeCurrencyUomId.isEmpty()) {
            storeCurrencyUomId = DEFAULT_CURRENCY_UOM;
        }
        
        //get product store website
        String storeWebSiteId;
        
        EntityCondition webSiteCond = EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, productStoreId);
        
        List<GenericValue> storeWebSiteList = null;
        
        try {
            storeWebSiteList = delegator.findList("WebSite", webSiteCond, null, null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee, "Error in retrieving web site record for product store ["+productStoreId+"].", module);
            storeWebSiteId = "";
        }
        
        GenericValue storeWebSite = null;
        
        if(UtilValidate.isNotEmpty(storeWebSiteList)) {
            
            storeWebSite = EntityUtil.getFirst(storeWebSiteList);
            
            storeWebSiteId = (String) storeWebSite.get("webSiteId");
            
        }else{
            storeWebSiteId = "";
        }
        
        
        //Check finished. Proceed.
        
        //Start retrieving all the products in the Channable catalog
        feedProductList = new ArrayList<>();
        List<GenericValue> catalogCategoryList = null;
        
        if(useSingleCategory) {
            
            //Retrieve the "Other Search" type category/ies for the catalog
            EntityCondition pcctCond = EntityCondition.makeCondition(EntityOperator.AND,
                                    EntityCondition.makeCondition("prodCatalogId", EntityOperator.EQUALS, channableCatalogId),
                                    EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, channableCategoryId),
                                    EntityCondition.makeCondition("prodCatalogCategoryTypeId", EntityOperator.EQUALS, "PCCT_OTHER_SEARCH"));
            
            
            try {
                catalogCategoryList = delegator.findList("ProdCatalogCategory", pcctCond, null, null, null, false);
            } catch (GenericEntityException gee) {
                String msg = "Error in retrieving ProdCatalogCategory for catalog id ["+channableCatalogId+"] and category ["+channableCategoryId+"]. Quit.";
                logger.logError(msg);
                Debug.logError(gee, msg, module);
                return ServiceUtil.returnError(msg);
            }
            
            //Exclude inactive categories
            catalogCategoryList = EntityUtil.filterByDate(catalogCategoryList);

            if(UtilValidate.isEmpty(catalogCategoryList)) {
                String msg = "No active categories PCCT_OTHER_SEARCH found associated to the catalog id ["+channableCatalogId+"] and category ["+channableCategoryId+"]. Quit.";
                logger.logError(msg);
                return ServiceUtil.returnError(msg);

            }
            
            
        }else {
            
            //Retrieve the "Other Search" type category/ies for the catalog
            EntityCondition pcctCond = EntityCondition.makeCondition(EntityOperator.AND,
                                    EntityCondition.makeCondition("prodCatalogId", EntityOperator.EQUALS, channableCatalogId),
                                    EntityCondition.makeCondition("prodCatalogCategoryTypeId", EntityOperator.EQUALS, "PCCT_OTHER_SEARCH"));

           
            try {
                catalogCategoryList = delegator.findList("ProdCatalogCategory", pcctCond, null, null, null, false);
            } catch (GenericEntityException gee) {
                String msg = "Error in retrieving ProdCatalogCategory for catalog id ["+channableCatalogId+"]. Quit.";
                logger.logError(msg);
                Debug.logError(gee, msg, module);
                return ServiceUtil.returnError(msg);
            }

            //Exclude inactive categories
            catalogCategoryList = EntityUtil.filterByDate(catalogCategoryList);

            if(UtilValidate.isEmpty(catalogCategoryList)) {
                String msg = "No categories PCCT_OTHER_SEARCH found associated to the catalog id ["+channableCatalogId+"]. Quit.";
                logger.logError(msg);
                return ServiceUtil.returnError(msg);

            }
            

        }
        
        //Add the product from each category
        for(GenericValue catalogCategory : catalogCategoryList) {

            String _productCategoryId = (String) catalogCategory.get("productCategoryId");

            List<GenericValue> _categoryMemberList = null;

            try {
                EntityCondition pcmCond = EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, _productCategoryId);

                _categoryMemberList = delegator.findList("ProductCategoryMember", pcmCond, null, null, null, false);

            }catch(GenericEntityException gee) {
                String msg = "Error in retrieving ProductCategoryMember for product catalog category with id ["+_productCategoryId+"]. Quit.";
                logger.logError(msg);
                Debug.logError(gee, msg, module);
                return ServiceUtil.returnError(msg);
            }

            _categoryMemberList = EntityUtil.filterByDate(_categoryMemberList);

            for(GenericValue _catMember : _categoryMemberList) {

                GenericValue _product = null;
                try {
                    _product = _catMember.getRelatedOne("Product", false);
                } catch (GenericEntityException gee) {
                    String msg = "Error in retrieving related Product entity for product category member with id ["+_catMember.getString("productId")+"]. Quit.";
                    logger.logError(msg);
                    Debug.logError(gee, msg, module);
                    return ServiceUtil.returnError(msg);
                }

                if(!feedProductList.contains(_product)) {
                    feedProductList.add(_product);
                }

            }


        }
        
        
        if(UtilValidate.isEmpty(feedProductList)) {
            String msg = "No products found in Channable catalog and categories. Cannot build feed.";
            logger.logError(msg);
            Debug.logError(msg, module);
            return ServiceUtil.returnError(msg);
        }
        
        //Filtering products by out of support date
        if(exlcudeOutOfSupportProducts) {
            feedProductList = ChannableWorker.filterOutOfSupportProducts(feedProductList, delegator);
        }
        
        //check if the catalog should be built by product variant
        Map<String, List<GenericValue>> productVariantMap = new HashMap<>();
        
        if(useVariantProduct) {
            
            for(GenericValue feedParentProduct : feedProductList) {
                
                String parentProductId = feedParentProduct.getString("productId");
                
                //Get all the variants of this product
                Map<String, Object> productVariantCtx = UtilMisc.toMap("productId", parentProductId);
                
                Map<String, Object> prdVariantResultOut = null;
                
                try {
                    prdVariantResultOut = dispatcher.runSync("getAllProductVariants", productVariantCtx);
                }catch(GenericServiceException gse) {
                    Debug.logError(gse, "Error in retrieving all product variants for product id ["+parentProductId+"].", module);
                    continue;
                }
                
                if(prdVariantResultOut != null && ServiceUtil.isSuccess(prdVariantResultOut)) {
                    
                    List<GenericValue> assocProductList = (List<GenericValue>) prdVariantResultOut.get("assocProducts");
                    
                    productVariantMap.put(parentProductId, assocProductList);
                }
                
            }
            
            logger.logError("Create XML feed by Parent Product");
            channOutMap = buildFeedByVariantProducts(productVariantMap, productStoreId, feedLocale, siteBaseUrl, 
                                                            itaWebapp, brand, googleProductCategoryId, storeCurrencyUomId, 
                                                            loginUsername, loginPassword, storeWebSiteId, delegator, dispatcher);
            
            
            
        }else{
            logger.logError("Create XML feed by Variant Product");
            channOutMap = buildFeedByParentProduct(feedProductList, productStoreId, feedLocale, siteBaseUrl, 
                                                            itaWebapp, brand, googleProductCategoryId, storeCurrencyUomId, 
                                                            loginUsername, loginPassword, storeWebSiteId, delegator, dispatcher);
        }
        
        
        //writing the xml document
        if(channOutMap != null && !channOutMap.isEmpty()) {
            
            logger.logError("Document created. Saving it.");
            
            Document channableDoc = (Document) channOutMap.get("channableDocument");
            feedProductCount = (int) channOutMap.get("feedProductCount");
            
            File output = new File(feedFileOutput);
            try {
                XMLUtil.saveDocument(channableDoc, new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
            } catch (FileNotFoundException ex) {
                Debug.logError(ex, "File not found: "+output.getName(), module);
            } catch (UnsupportedEncodingException ex) {
                Debug.logError(ex, module);
            }
        }
     
        logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
        
        return ServiceUtil.returnSuccess("Successfully exported "+feedProductCount+" products into the feed file.");
        
        
    }
    
    
    /**
     * Build xml channable document by parent product
     * 
     * @param feedProductList
     * @param productStoreId
     * @param feedLocale
     * @param siteBaseUrl
     * @param itaWebapp
     * @param brand
     * @param googleProductCategoryId
     * @param storeCurrencyUomId
     * @param storeWebSiteId
     * @param loginUsername
     * @param loginPassword
     * @param delegator
     * @param dispatcher
     * @return 
     */
    private static Map<String, Object> buildFeedByParentProduct(List<GenericValue> feedProductList, String productStoreId, Locale feedLocale,
                                                        String siteBaseUrl, String itaWebapp, String brand, String googleProductCategoryId,
                                                        String storeCurrencyUomId, String storeWebSiteId, String loginUsername, 
                                                        String loginPassword, Delegator delegator, LocalDispatcher dispatcher) {
        
        Map<String, Object> outMap = new HashMap<>();
        int feedProductCount = 0;
        Document channableDoc = null;
        
        if(UtilValidate.isNotEmpty(feedProductList)) {
            
            Map<String, Boolean> productStockStatusMap = ChannableWorker.getParentProductStockStatus(feedProductList, productStoreId, delegator, dispatcher);
            
            //Create a new feed document
            channableDoc = XMLUtil.createDocument();
            
            if(channableDoc != null) {
                
                Element items_el = XMLUtil.createXMLElement(channableDoc, "items", null, null);
                
                //loop the product to build each <item>....</item> eleemnt
                for(GenericValue feedProduct : feedProductList) {
                    
                    ProductContentWrapper pcw = new ProductContentWrapper(dispatcher, feedProduct, feedLocale,"text/html");
                    
                    
                    //create the main <item> element
                    Element item_el = XMLUtil.createXMLElement(channableDoc, "item", null, null);
                    
                    
                    //Item id (M)
                    String productId = (String) feedProduct.get("productId");
                    Element prodId_el = XMLUtil.createXMLElement(channableDoc, "id", null, productId);
                    
                    //Item description (M)
                    String itemDescription = (pcw.get("LONG_DESCRIPTION", "html")).toString();
                    
                    if(itemDescription.isEmpty()) {
                        itemDescription = (pcw.get("PRODUCT_NAME", "html")).toString();
                    }
                    
                    Element descr_el = XMLUtil.createXMLElement(channableDoc, "description", null, itemDescription);
                    
                    //Item main image (M)
                    String largeImageUrl = (String) feedProduct.get("largeImageUrl");
                    String image_link = "";
                    
                    if(!siteBaseUrl.endsWith(File.separator)) {
                        image_link = siteBaseUrl + File.separator + largeImageUrl;
                    }else{
                        image_link = siteBaseUrl + largeImageUrl;
                    }
                    
                    Element imglink_el = XMLUtil.createXMLElement(channableDoc, "image_link", null, image_link);
                    
                    //Item link (M)
                    String alternativeUrl = ProductContentWrapper.getProductContentAsText(feedProduct, "ALTERNATIVE_URL", feedLocale, dispatcher, "html");
                    String productName = ProductContentWrapper.getProductContentAsText(feedProduct, "PRODUCT_NAME", feedLocale, dispatcher, "html");
                    String productUri = "";
                    
                    if (alternativeUrl != null && !alternativeUrl.isEmpty()) {
                        alternativeUrl = UrlServletHelper.invalidCharacter(alternativeUrl);
                        productUri = alternativeUrl + "-" + productId + "-p";
                    } else {
                        //use the product name
                        productName = UrlServletHelper.invalidCharacter(productName);
                        productUri = productName + "-" + productId + "-p";
                       
                    }
                    
                    String productLink = "";
                    
                    if(!siteBaseUrl.endsWith(File.separator)) {
                        productLink = siteBaseUrl + File.separator + itaWebapp + File.separator + productUri;
                    }else{
                        productLink = siteBaseUrl + itaWebapp + File.separator + productUri;
                    }
                    
                    Element prdlink_el = XMLUtil.createXMLElement(channableDoc, "link", null, productLink);
                    
                    //Item title (M)
                    Element prdtitle_el = XMLUtil.createXMLElement(channableDoc, "title", null, productName);
                    
                    //Brand (M)
                    Element brand_el = XMLUtil.createXMLElement(channableDoc, "brand", null, brand);
                    
                    //Item stock status (M)
                    Boolean inStock = productStockStatusMap.get(productId);
                    String stockStatus = "";
                    if(inStock) {
                        stockStatus = IN_STOCK_STR;
                    }else{
                        stockStatus = OUTOF_STOCK_STR;
                    }
                    
                    Element availability_el = XMLUtil.createXMLElement(channableDoc, "availability", null, stockStatus);
                    
                    //Item type (M)
                    //Get primary category id from product
                    String productCategoryId = "";
                    String productType = "";
                    if(feedProduct.get("primaryProductCategoryId") != null) {
                        productCategoryId = (String) feedProduct.get("primaryProductCategoryId");
                        
                        GenericValue productCategory = null;
                        
                        try {
                            productCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", productCategoryId), false);
                        }catch(GenericEntityException gee) {
                            Debug.logError(gee, "Error in retrieving product category with id ["+productCategoryId+"].", module);
                        }
                        
                        if(productCategory != null) {
                            
                            CategoryContentWrapper ccw = new CategoryContentWrapper(dispatcher, productCategory, feedLocale, "text/html");
                            
                            String categoryName = "";
                            
                            categoryName = (ccw.get("CATEGORY_NAME", "html")).toString();
                            
                            if(categoryName == null) {
                                categoryName = CategoryContentWrapper.getProductCategoryContentAsText(productCategory, "CATEGORY_NAME", feedLocale, dispatcher, "html");
                            }
                            
                            if(categoryName!= null && !categoryName.isEmpty()) {
                                productType = categoryName;
                            }else{
                                productType="-";
                            }
                        }
                        
                    }else{
                        productType="-";
                    }
                    
                    Element prdtype_el = XMLUtil.createXMLElement(channableDoc, "product_type", null, productType);
                   
                    
                    //Item list price (M)
                    BigDecimal listPrice = BigDecimal.ZERO;
                    BigDecimal price = BigDecimal.ZERO;
                    HashMap<String, Object> productPriceCtx = new HashMap<>();
                    
                    productPriceCtx.put("product", feedProduct);
                    productPriceCtx.put("currencyUomId", storeCurrencyUomId);
                    if(!storeWebSiteId.isEmpty()) {
                        productPriceCtx.put("webSiteId", storeWebSiteId);

                    }
                    
                    productPriceCtx.put("productStoreId", productStoreId);
                    productPriceCtx.put("login.username", loginUsername);
                    productPriceCtx.put("login.password", loginPassword);
                    
                    Map<String, Object> priceResultOut = null;
                    
                    try {
                        priceResultOut = dispatcher.runSync("calculateProductPrice", productPriceCtx);
                    }catch(GenericServiceException gse) {
                        Debug.logError(gse, "Error in calculating prices for product ["+productId+"], store ["+productStoreId+"], currency ["+storeCurrencyUomId+"]", module);
                    }

                    //Get prices
                    if(priceResultOut!= null && ServiceUtil.isSuccess(priceResultOut)) {
                        
                        boolean validPriceFound = (Boolean) priceResultOut.get("validPriceFound");
                        
                        if(validPriceFound) {
                            
                            listPrice = (BigDecimal) priceResultOut.get("listPrice");
                            price = (BigDecimal) priceResultOut.get("price"); //this is the final price
                            
                        }
                    }
                    
                    String listPriceCurrency = listPrice.toPlainString() + " " + storeCurrencyUomId;
                    String salePriceCurrency = price.toPlainString() + " " + storeCurrencyUomId;
                    
                    Element listprice_el = XMLUtil.createXMLElement(channableDoc, "price", null, listPriceCurrency);
                    
                    Element saleprice_el = XMLUtil.createXMLElement(channableDoc, "sale_price", null, salePriceCurrency);
                    
                    //Product sizes (M)
                    StringBuilder sizeList = new StringBuilder();
                    Map<String, Object> featuresTypeCtx = new HashMap<>();
                    Map<String, Object> featuresTypeResultOut = null;
                    featuresTypeCtx.put("productId", productId);
                    featuresTypeCtx.put("productFeatureApplTypeId", "SELECTABLE_FEATURE");
                    
                    try {
                        featuresTypeResultOut = dispatcher.runSync("getProductFeaturesByType", featuresTypeCtx);
                    }catch(GenericServiceException gse) {
                        Debug.logError(gse, "Error in retreiving selectable features for product ["+productId+"]", module);
                    }
                    
                    if(featuresTypeResultOut!= null && ServiceUtil.isSuccess(featuresTypeResultOut)) {
                        
                       Map<String, Object> featuresByType = (Map) featuresTypeResultOut.get("productFeaturesByType");
                       
                       for(Entry<String, Object> entry : featuresByType.entrySet()) {
                           
                           String _key = entry.getKey();
                           List<GenericValue> _value = (List<GenericValue>) entry.getValue();
                           
                           System.out.println("_key: "+_key);
                           
                           for(GenericValue feature : _value) {
                               String sizeDescr = (String) feature.get("description");
                               sizeList.append(sizeDescr).append("/");
                               
                           }
                           
                       }
                    }
                    
                    Element size_el = XMLUtil.createXMLElement(channableDoc, "size", null, sizeList.toString());
                    
                    //Additional images (R)
                    String additionalImages = ChannableWorker.getProductAdditionalImages(pcw, 3, siteBaseUrl);
                    Element addImgs_el = null;
                    
                    if(additionalImages != null && !additionalImages.isEmpty()) {
                        addImgs_el = XMLUtil.createXMLElement(channableDoc, "additional_image_link", null, additionalImages);
                    }
                    
                    
                    //Google product category (R)
                    Element googlePrdCat_el = XMLUtil.createXMLElement(channableDoc, "google_product_category", null, googleProductCategoryId);
                    
                    //Good Identification Number (R)
                    String goodIdentNum = ChannableWorker.getProductGoodIdentificationNumber(productId, EAN_GOOD_IDENT_TYPE, delegator);
                    Element goodIdentNum_el = null;
                    
                    if(goodIdentNum != null && !goodIdentNum.isEmpty()) {
                        goodIdentNum_el = XMLUtil.createXMLElement(channableDoc, "ean", null, goodIdentNum);
                    }
                    
                    //Product composition (R)
                    String productComposition = (pcw.get("INGREDIENTS", "html")).toString();
                    Element composition_el = null;
                    if(productComposition != null && !productComposition.isEmpty()) {
                        composition_el = XMLUtil.createXMLElement(channableDoc, "material", null, productComposition);
                    }
                    
                    //Product condition (R)
                    Element productCondition_el = XMLUtil.createXMLElement(channableDoc, "condition", null, PRODUCT_NEW_CONDITION);
                    
                    //Product color (R)
                    String color = (String) feedProduct.get("comments");
                    Element color_el = null;
                    if(color != null && !color.isEmpty()) {
                        color_el = XMLUtil.createXMLElement(channableDoc, "color", null, color);
                    }
                    
                    //Product Gender (R)
                    Element gender_el = XMLUtil.createXMLElement(channableDoc, "gender", null, PRODUCT_GENDER_F);
                    
                    //Custom Label 0 (custom field)
                    String season = productId.substring(0, 2);
                    
                    Element custom_label_0_el = XMLUtil.createXMLElement(channableDoc, "custom_label_0", null, season);
                    
                    
                    //appending all the MANDATORY elements
                    item_el.appendChild(prodId_el);
                    item_el.appendChild(prdtitle_el);
                    item_el.appendChild(brand_el);
                    item_el.appendChild(descr_el);
                    item_el.appendChild(imglink_el);
                    item_el.appendChild(prdlink_el);
                    item_el.appendChild(availability_el);
                    item_el.appendChild(prdtype_el);
                    item_el.appendChild(listprice_el);
                    item_el.appendChild(saleprice_el);
                    item_el.appendChild(size_el);
                    
                    
                    //appending all the RECOMMENDED/OPTIONAL elements
                    if(addImgs_el != null) {
                        item_el.appendChild(addImgs_el);
                    }
                    
                    if(googlePrdCat_el != null) {
                        item_el.appendChild(googlePrdCat_el);
                    }
                    
                    if(goodIdentNum_el != null) {
                        item_el.appendChild(goodIdentNum_el);
                    }
                    
                    if(composition_el != null) {
                        item_el.appendChild(composition_el);
                    }
                    
                    if(productCondition_el != null) {
                        item_el.appendChild(productCondition_el);
                    }
                    
                    if(color_el != null) {
                        item_el.appendChild(color_el);
                    }
                    
                    if(gender_el != null) {
                        item_el.appendChild(gender_el);
                    }
                    
                    if(custom_label_0_el != null) {
                        item_el.appendChild(custom_label_0_el);
                    }
                    
                    //append the <item> to the main <items> element
                    items_el.appendChild(item_el);
                    
                    feedProductCount++;
                    
                }
                
                channableDoc.appendChild(items_el);
                
                outMap.put("channableDocument", channableDoc);
                outMap.put("feedProductCount", feedProductCount);
                
                
            }
            
            
        }//feed product list is empty
            
        return outMap;
    }
    
    /**
     * 
     * @param productVariantMap <parentProductId, List<ProductVariants>>
     * @param productStoreId
     * @param feedLocale
     * @param siteBaseUrl
     * @param itaWebapp
     * @param brand
     * @param googleProductCategoryId
     * @param storeCurrencyUomId
     * @param storeWebSiteId
     * @param loginUsername
     * @param loginPassword
     * @param delegator
     * @param dispatcher
     * @return 
     */
    private static Map<String, Object> buildFeedByVariantProducts(Map<String, List<GenericValue>> productVariantMap, String productStoreId, Locale feedLocale,
                                                        String siteBaseUrl, String itaWebapp, String brand, String googleProductCategoryId,
                                                        String storeCurrencyUomId, String storeWebSiteId, String loginUsername, 
                                                        String loginPassword, Delegator delegator, LocalDispatcher dispatcher) {
        
        Map<String, Object> outMap = new HashMap<>();
        int feedProductCount = 0;
        Document channableDoc = null;
        
        if(UtilValidate.isNotEmpty(productVariantMap)) {
            
            //Create a new feed document
            channableDoc = XMLUtil.createDocument();
            
            if(channableDoc != null) {
                
                Element items_el = XMLUtil.createXMLElement(channableDoc, "items", null, null);
                
                for(Entry<String, List<GenericValue>> entry : productVariantMap.entrySet()) {
                    
                    String parentProductId = entry.getKey();
                    
                    GenericValue parentProduct = null;
                    
                    try {
                        parentProduct = delegator.findOne("Product", UtilMisc.toMap("productId", parentProductId), false);
                    }catch(GenericEntityException gee) {
                        Debug.logError(gee, "Error in retrieving parent product record for product id ["+parentProductId+"].", module);
                        continue;
                    }
                    
                    ProductContentWrapper pcw = new ProductContentWrapper(dispatcher, parentProduct, feedLocale,"text/html");
                    
                    List<GenericValue> productVariants = (List<GenericValue>) entry.getValue();
                    
                    List<GenericValue> variants = new ArrayList<>();
                    //loop the ProductAssoc of type PRODUCT_VARIANT and retrieve the related Product
                    for(GenericValue variantAssoc : productVariants) {
                        
                        GenericValue variantProduct = null;
                        
                        String productIdTo = (String) variantAssoc.get("productIdTo");
                        
                        
                        try {
                            variantProduct = delegator.findOne("Product", UtilMisc.toMap("productId", productIdTo), false);
                        }catch(GenericEntityException gee) {
                            Debug.logError(gee, "Error in retrieving Product for variantAssoc ["+variantAssoc+"]", module);
                        }
                        
                        if(variantProduct == null) {
                            Debug.logWarning("Varinat prouct is null. Skip and continue", module);
                            continue;
                        }
                        
                        if(!variants.contains(variantProduct)) {
                            variants.add(variantProduct);
                        }
                        
                        //stock status for variants
                        Map<String, Boolean> productStockStatusMap = ChannableWorker.getVariantProductStockStatus(variants, productStoreId, delegator, dispatcher);
                        
                        //create the main <item> element
                        Element item_el = XMLUtil.createXMLElement(channableDoc, "item", null, null);
                    
                        //Item id (M)
                        String variantProductId = (String) variantAssoc.get("productIdTo");
                        Element prodId_el = XMLUtil.createXMLElement(channableDoc, "id", null, variantProductId);

                        //Item description (M)
                        String itemDescription = (pcw.get("LONG_DESCRIPTION", "html")).toString();

                        if(itemDescription.isEmpty()) {
                            itemDescription = (pcw.get("PRODUCT_NAME", "html")).toString();
                        }

                        Element descr_el = XMLUtil.createXMLElement(channableDoc, "description", null, itemDescription);
                        
                        //Item main image (M)
                        String largeImageUrl = (String) parentProduct.get("largeImageUrl");
                        String image_link = "";

                        if(!siteBaseUrl.endsWith(File.separator)) {
                            image_link = siteBaseUrl + File.separator + largeImageUrl;
                        }else{
                            image_link = siteBaseUrl + largeImageUrl;
                        }

                        Element imglink_el = XMLUtil.createXMLElement(channableDoc, "image_link", null, image_link);

                        //Item link (M)
                        String alternativeUrl = ProductContentWrapper.getProductContentAsText(parentProduct, "ALTERNATIVE_URL", feedLocale, dispatcher,  "html");
                        String productName = ProductContentWrapper.getProductContentAsText(parentProduct, "PRODUCT_NAME", feedLocale, dispatcher, "html");
                        String productUri = "";

                        if (alternativeUrl != null && !alternativeUrl.isEmpty()) {
                            alternativeUrl = UrlServletHelper.invalidCharacter(alternativeUrl);
                            productUri = alternativeUrl + "-" + parentProductId + "-p";
                        } else {
                            //use the product name
                            productName = UrlServletHelper.invalidCharacter(productName);
                            productUri = productName + "-" + parentProductId + "-p";

                        }

                        String productLink = "";

                        if(!siteBaseUrl.endsWith(File.separator)) {
                            productLink = siteBaseUrl + File.separator + itaWebapp + File.separator + productUri;
                        }else{
                            productLink = siteBaseUrl + itaWebapp + File.separator + productUri;
                        }

                        Element prdlink_el = XMLUtil.createXMLElement(channableDoc, "link", null, productLink);

                        //Item title (M)
                        Element prdtitle_el = XMLUtil.createXMLElement(channableDoc, "title", null, productName);

                        //Brand (M)
                        Element brand_el = XMLUtil.createXMLElement(channableDoc, "brand", null, brand);
                        
                        //Item stock status (M)
                        Boolean inStock = productStockStatusMap.get(variantProductId);
                        String stockStatus = "";
                        if(inStock) {
                            stockStatus = IN_STOCK_STR;
                        }else{
                            stockStatus = OUTOF_STOCK_STR;
                        }

                        Element availability_el = XMLUtil.createXMLElement(channableDoc, "availability", null, stockStatus);
                        
                        //Item type (M)
                        //Get primary category id from product
                        String productCategoryId = "";
                        String productType = "";
                        if(parentProduct.get("primaryProductCategoryId") != null) {
                            productCategoryId = (String) parentProduct.get("primaryProductCategoryId");

                            GenericValue productCategory = null;

                            try {
                                productCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", productCategoryId), false);
                            }catch(GenericEntityException gee) {
                                Debug.logError(gee, "Error in retrieving product category with id ["+productCategoryId+"].", module);
                            }

                            if(productCategory != null) {

                                CategoryContentWrapper ccw = new CategoryContentWrapper(dispatcher, productCategory, feedLocale, "text/html");

                                String categoryName = "";

                                categoryName = (ccw.get("CATEGORY_NAME", "html")).toString();

                                if(categoryName == null) {
                                    categoryName = CategoryContentWrapper.getProductCategoryContentAsText(productCategory, "CATEGORY_NAME", feedLocale, dispatcher, "html");
                                }

                                if(categoryName!= null && !categoryName.isEmpty()) {
                                    productType = categoryName;
                                }else{
                                    productType="-";
                                }
                            }

                        }else{
                            productType="-";
                        }

                        Element prdtype_el = XMLUtil.createXMLElement(channableDoc, "product_type", null, productType);
                        
                        //Item list price (M)
                        BigDecimal listPrice = BigDecimal.ZERO;
                        BigDecimal price = BigDecimal.ZERO;
                        HashMap<String, Object> productPriceCtx = new HashMap<>();

                        productPriceCtx.put("product", variantProduct);
                        productPriceCtx.put("currencyUomId", storeCurrencyUomId);
                        if(!storeWebSiteId.isEmpty()) {
                            productPriceCtx.put("webSiteId", storeWebSiteId);

                        }

                        productPriceCtx.put("productStoreId", productStoreId);
                        productPriceCtx.put("login.username", loginUsername);
                        productPriceCtx.put("login.password", loginPassword);

                        Map<String, Object> priceResultOut = null;

                        try {
                            priceResultOut = dispatcher.runSync("calculateProductPrice", productPriceCtx);
                        }catch(GenericServiceException gse) {
                            Debug.logError(gse, "Error in calculating prices for product ["+variantProductId+"], store ["+productStoreId+"], currency ["+storeCurrencyUomId+"]", module);
                        }

                        //Get prices
                        if(priceResultOut!= null && ServiceUtil.isSuccess(priceResultOut)) {

                            boolean validPriceFound = (Boolean) priceResultOut.get("validPriceFound");

                            if(validPriceFound) {

                                listPrice = (BigDecimal) priceResultOut.get("listPrice");
                                price = (BigDecimal) priceResultOut.get("price"); //this is the final price

                            }
                        }

                        String listPriceCurrency = listPrice.toPlainString() + " " + storeCurrencyUomId;
                        String salePriceCurrency = price.toPlainString() + " " + storeCurrencyUomId;

                        Element listprice_el = XMLUtil.createXMLElement(channableDoc, "price", null, listPriceCurrency);

                        Element saleprice_el = XMLUtil.createXMLElement(channableDoc, "sale_price", null, salePriceCurrency);
                        
                        //Product sizes (M)
                        String sizeDescr = null;
                        Map<String, Object> productFeaturesCtx = new HashMap<>();
                        Map<String, Object> productFeaturesResultOut = null;
                        productFeaturesCtx.put("productId", variantProductId);
                        productFeaturesCtx.put("type", "STANDARD_FEATURE");
                        productFeaturesCtx.put("distinct", "SIZE");

                        try {
                            productFeaturesResultOut = dispatcher.runSync("getProductFeatures", productFeaturesCtx);
                        }catch(GenericServiceException gse) {
                            Debug.logError(gse, "Error in retreiving selectable features for product ["+variantProductId+"]", module);
                        }

                        if(productFeaturesResultOut!= null && ServiceUtil.isSuccess(productFeaturesResultOut)) {

                           List<GenericValue> productFeatures = (List<GenericValue>) productFeaturesResultOut.get("productFeatures");
                           
                           if(UtilValidate.isNotEmpty(productFeatures)) {
                               
                               GenericValue sizeFeature = EntityUtil.getFirst(productFeatures);
                               
                               sizeDescr = (String) sizeFeature.get("description");

                           }else{
                               
                               sizeDescr = "-";
                               
                           }
                        }

                        Element size_el = XMLUtil.createXMLElement(channableDoc, "size", null, sizeDescr);
                        
                        //Additional images (R)
                        String additionalImages = ChannableWorker.getProductAdditionalImages(pcw, 3, siteBaseUrl);
                        Element addImgs_el = null;

                        if(additionalImages != null && !additionalImages.isEmpty()) {
                            addImgs_el = XMLUtil.createXMLElement(channableDoc, "additional_image_link", null, additionalImages);
                        }


                        //Google product category (R)
                        Element googlePrdCat_el = XMLUtil.createXMLElement(channableDoc, "google_product_category", null, googleProductCategoryId);

                        //Good Identification Number (R)
                        String goodIdentNum = ChannableWorker.getProductGoodIdentificationNumber(variantProductId, EAN_GOOD_IDENT_TYPE, delegator);
                        Element goodIdentNum_el = null;

                        if(goodIdentNum != null && !goodIdentNum.isEmpty()) {
                            goodIdentNum_el = XMLUtil.createXMLElement(channableDoc, "ean", null, goodIdentNum);
                        }

                        //Product composition (R)
                        String productComposition = (pcw.get("INGREDIENTS", "html")).toString();
                        Element composition_el = null;
                        if(productComposition != null && !productComposition.isEmpty()) {
                            composition_el = XMLUtil.createXMLElement(channableDoc, "material", null, productComposition);
                        }

                        //Product condition (R)
                        Element productCondition_el = XMLUtil.createXMLElement(channableDoc, "condition", null, PRODUCT_NEW_CONDITION);

                        //Product color (R)
                        String color = (String) parentProduct.get("comments");
                        Element color_el = null;
                        if(color != null && !color.isEmpty()) {
                            color_el = XMLUtil.createXMLElement(channableDoc, "color", null, color);
                        }

                        //Product Gender (R)
                        Element gender_el = XMLUtil.createXMLElement(channableDoc, "gender", null, PRODUCT_GENDER_F);
                        
                        //Parent Group ID (Id of the parent product) (R)
                        Element item_group_id_el = XMLUtil.createXMLElement(channableDoc, "item_group_id", null, parentProductId);
                        
                        //Custom Label 0 (custom field)
                        String season = variantProductId.substring(0, 2);

                        Element custom_label_0_el = XMLUtil.createXMLElement(channableDoc, "custom_label_0", null, season);
                        
                        
                        //Appending MANDATORY fields
                        item_el.appendChild(prodId_el);
                        item_el.appendChild(prdtitle_el);
                        item_el.appendChild(brand_el);
                        item_el.appendChild(descr_el);
                        item_el.appendChild(imglink_el);
                        item_el.appendChild(prdlink_el);
                        item_el.appendChild(availability_el);
                        item_el.appendChild(prdtype_el);
                        item_el.appendChild(listprice_el);
                        item_el.appendChild(saleprice_el);
                        item_el.appendChild(size_el);
                        
                        //appending all the RECOMMENDED/OPTIONAL elements
                        if(addImgs_el != null) {
                            item_el.appendChild(addImgs_el);
                        }

                        if(googlePrdCat_el != null) {
                            item_el.appendChild(googlePrdCat_el);
                        }

                        if(goodIdentNum_el != null) {
                            item_el.appendChild(goodIdentNum_el);
                        }

                        if(composition_el != null) {
                            item_el.appendChild(composition_el);
                        }

                        if(productCondition_el != null) {
                            item_el.appendChild(productCondition_el);
                        }

                        if(color_el != null) {
                            item_el.appendChild(color_el);
                        }

                        if(gender_el != null) {
                            item_el.appendChild(gender_el);
                        }
                        
                        if(item_group_id_el != null) {
                            item_el.appendChild(item_group_id_el);
                        }
                        
                        if(custom_label_0_el != null) {
                            item_el.appendChild(custom_label_0_el);
                        }
                        
                        //append the <item> to the main <items> element
                        items_el.appendChild(item_el);

                        feedProductCount++;
                        
                    
                    }//end looping variants for a product
                    
                }//end looping parent products
                
                channableDoc.appendChild(items_el);
                
                outMap.put("channableDocument", channableDoc);
                outMap.put("feedProductCount", feedProductCount);
                
            }
            
        }
        
        return outMap;
        
        
    }
    
}//end class
