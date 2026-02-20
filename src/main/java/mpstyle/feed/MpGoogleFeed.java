package mpstyle.feed;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import mpstyle.log.FeedLogger;
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
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import java.math.*;


public class MpGoogleFeed 
{
	public static final String module = MpGoogleFeed.class.getName();
	
	private static String systemResourceId = "mpstyle";
    
    private final static String DEFAULT_LOCALE = "it_IT";
    private final static String DEFAULT_CURRENCY_UOM = "EUR";
    private final static String DEFAULT_COUNTRY_GEO = "ITA";
    private final static String IN_STOCK_STR = "in stock";
    private final static String OUTOF_STOCK_STR = "out of stock";
    private final static String EAN_GOOD_IDENT_TYPE = "EAN";
    private final static String PRODUCT_NEW_CONDITION = "new";
    private final static String IDENTIFIER_EXISTS = "yes";
    private final static String GOOGLE_PRODUCT_CATEGORY_ID = "GOOGLE";

    
	public Map<String, Object> createFeedXml(DispatchContext ctx, Map<String, ? extends Object> context)
	{
		Document doc = createDocument();
		if (doc != null) 
		{
			 try 
			 {
	                Element el_rss = doc.createElement("rss");
	                el_rss.setAttribute("version", "2.0");
	                el_rss.setAttribute("xmlns:g", "http://base.google.com/ns/1.0");

	                Element el_channel = doc.createElement("channel");
	                Element el_temp;

	                Delegator delegator = ctx.getDelegator();
	                LocalDispatcher dispatcher = ctx.getDispatcher();
	                MathContext mathRound = new MathContext(2);
	                
	                String loginUsername = EntityUtilProperties.getPropertyValue(systemResourceId, "serviceUsername", delegator);
	                String loginPassword = EntityUtilProperties.getPropertyValue(systemResourceId, "servicePassword", delegator);
	                
	                // parametri richiesti in input.
	                String title = (String) context.get("title feed");
	                String link = (String) context.get("link feed");
	                String descriptionBrand = (String) context.get("brand feed description");
	                String feedFileName = (String) context.get("feed file name");
	                String feedFileOutputDir = (String) context.get("feed out path");
	                String siteBaseUrl = (String) context.get("siteBaseUrl");
	                String logfilename = (String) context.get("logfilename");
	                String brand = (String) context.get("brand"); // Vivis Intimo
	                String google_product_category = (String) context.get("googleProductCategoryId");
	                String productStoreId = (String) context.get("productStoreId"); //10000
	                String webapp = (String) context.get("webapp"); //10000
	                
	                el_temp = doc.createElement("title");
	                el_temp.setTextContent(title);
	                el_channel.appendChild(el_temp);

	                el_temp = doc.createElement("link");
	                el_temp.setTextContent(link);
	                el_channel.appendChild(el_temp);

	                el_temp = doc.createElement("description");
	                el_temp.setTextContent(descriptionBrand);

	                el_channel.appendChild(el_temp);
	                
	                
	                String feedFileOutput = "";
	                Locale feedLocale = null;
	                
	                if(!feedFileOutputDir.endsWith(File.separator)) 
	                {
	                    feedFileOutput = feedFileOutputDir + File.separator + feedFileName;
	                
	                }else{
	                    
	                	feedFileOutput = feedFileOutputDir + feedFileName;
	                }
	                
	                FeedLogger logger = new FeedLogger(delegator.getDelegatorTenantId(), logfilename);
	                
	                //Check existance of product store
	                GenericValue productStore = null;
	                
	                try 
	                {
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
	                
	                logger.logInfo("************* productStoreLocale: "+productStoreLocale);
	                
	                if(productStoreLocale == null) 
	                {
	                    productStoreLocale = DEFAULT_LOCALE;
	                }
	                
	                feedLocale = UtilMisc.parseLocale(productStoreLocale);
	                
	                String storeCurrencyUomId = (String) productStore.get("defaultCurrencyUomId");
	                
	                //set a default currency
	                if(storeCurrencyUomId.isEmpty()) 
	                {
	                    storeCurrencyUomId = DEFAULT_CURRENCY_UOM;
	                }
	                
	                //get product store website
	                String storeWebSiteId;
	                
	                EntityCondition webSiteCond = EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, productStoreId);
	                
	                List<GenericValue> storeWebSiteList = null;
	                
	                try 
	                {
	                    storeWebSiteList = delegator.findList("WebSite", webSiteCond, null, null, null, false);
	                
	                }catch(GenericEntityException gee) {
	                	String msg = "Error in retrieving web site record for product store ["+productStoreId+"].";
	                    logger.logError(msg);
	                	Debug.logError(gee, "Error in retrieving web site record for product store ["+productStoreId+"].", module);
	                    storeWebSiteId = "";
	                }
	                
	                GenericValue storeWebSite = null;
	                
	                if(UtilValidate.isNotEmpty(storeWebSiteList))
	                {
	                    storeWebSite = EntityUtil.getFirst(storeWebSiteList);
	                    
	                    storeWebSiteId = (String) storeWebSite.get("webSiteId");
	                    
	                }else{
	                    
	                	storeWebSiteId = "";
	                }

                    logger.logInfo("************* storeWebSiteId: "+storeWebSiteId);
	                
	                Map<String, String> shippingEstimateMap = new HashMap<>();
                    
                    EntityCondition condition = EntityCondition.makeCondition(EntityOperator.AND,
                            EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, productStoreId),
                            EntityCondition.makeCondition("roleTypeId", EntityOperator.EQUALS, "CARRIER"));

                    List<GenericValue> shipEstList = delegator.findList("ShipmentCostEstimate", condition, null, null, null, false);
                    
                    for (GenericValue shipEst : shipEstList) 
                    {
                        String geoIdTo = (String) shipEst.get("geoIdTo");
                        
                        String geoCode = null;
                        
                        GenericValue geoGenVal = delegator.findOne("Geo", UtilMisc.toMap("geoId", geoIdTo), false);
                        
                        if(geoGenVal != null)
                        {
                        	geoCode = (String)geoGenVal.get("geoCode");
                        }
                        
                        BigDecimal shipEstimate = (BigDecimal) shipEst.get("orderFlatPrice");
                        
                        String shipEstimateStr = shipEstimate.toPlainString();
                        
                        shippingEstimateMap.putIfAbsent(geoCode, shipEstimateStr);
                       
                    }
                    //end for productStoreShipMethList
	                
	                // retrieve product from googleProductCategoryId

                    List<GenericValue> productCategoryMemberList = null;

                    try 
                    {
                        EntityCondition pcmCond = EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, GOOGLE_PRODUCT_CATEGORY_ID);

                        productCategoryMemberList = delegator.findList("ProductCategoryMember", pcmCond, null, null, null, false);

	                }catch(GenericEntityException gee) {
	                        String msg = "Error in retrieving ProductCategoryMember for product catalog category with id ["+GOOGLE_PRODUCT_CATEGORY_ID+"]. Quit.";
	                        logger.logError(msg);
	                        Debug.logError(gee, msg, module);
	                        return ServiceUtil.returnError(msg);
	                }
                    
                    
                    if(!UtilValidate.isEmpty(productCategoryMemberList)) {
                        
                    	productCategoryMemberList = EntityUtil.filterByDate(productCategoryMemberList);
                    	
                    }else {
                    	String msg = "No products found in Channable catalog and categories. Cannot build feed.";
                        logger.logError(msg);
                        Debug.logError(msg, module);
                        return ServiceUtil.returnError(msg);
                    }
                    
                    
                    //check if the catalog should be built by product variant
                    Map<String, List<GenericValue>> productVariantMap = new HashMap<>();
                   
                        
                    for(GenericValue productCategoryMember : productCategoryMemberList) 
                    {
                    
                    	String parentProductId = productCategoryMember.getString("productId");

                        logger.logInfo("************* parentProductId: "+parentProductId);
                            
                        //Get all the variants of this product
                        Map<String, Object> productVariantCtx = UtilMisc.toMap("productId", parentProductId);
                            
                        Map<String, Object> prdVariantResultOut = null;
                            
                        try 
                        {
                                prdVariantResultOut = dispatcher.runSync("getAllProductVariants", productVariantCtx);
                        
                        }catch(GenericServiceException gse) {
                        		String msg = "Error in retrieving all product variants for product id ["+parentProductId+"].";
                        		logger.logError(msg);
                                Debug.logError(gse, "Error in retrieving all product variants for product id ["+parentProductId+"].", module);
                                continue;
                        }
                            
                        if(prdVariantResultOut != null && ServiceUtil.isSuccess(prdVariantResultOut)) 
                        {
                                
                        	List<GenericValue> assocProductList = (List<GenericValue>) prdVariantResultOut.get("assocProducts");
                                
                            productVariantMap.put(parentProductId, assocProductList);
                        }
                            
                    }
                        
                    
                    if(UtilValidate.isNotEmpty(productVariantMap)) 
                    {
                    	for(Entry<String, List<GenericValue>> entry : productVariantMap.entrySet()) 
                    	{
                            
                            String parentProductId = entry.getKey();
                            
                            GenericValue parentProduct = null;
                            
                            try 
                            {
                                parentProduct = delegator.findOne("Product", UtilMisc.toMap("productId", parentProductId), false);
                            
                            }catch(GenericEntityException gee) {
                            	String msg = "Error in retrieving parent product record for product id ["+parentProductId+"].";
                        		logger.logError(msg);
                                Debug.logError(gee, "Error in retrieving parent product record for product id ["+parentProductId+"].", module);
                                continue;
                            }
                            
                            ProductContentWrapper pcw = new ProductContentWrapper(dispatcher, parentProduct, feedLocale,"text/html");
                    	
                            List<GenericValue> productVariants = (List<GenericValue>) entry.getValue();
                            
                            List<GenericValue> variants = new ArrayList<>();
                            //loop the ProductAssoc of type PRODUCT_VARIANT and retrieve the related Product
                            for(GenericValue variantAssoc : productVariants) 
                            {
                            	GenericValue variantProduct = null;
                                
                                String productIdTo = (String) variantAssoc.get("productIdTo");

                                logger.logInfo("************* productIdTo: "+productIdTo);
                                
                                try 
                                {
                                    variantProduct = delegator.findOne("Product", UtilMisc.toMap("productId", productIdTo), false);
                                
                                }catch(GenericEntityException gee) {
                                	String msg = "Error in retrieving Product for variantAssoc ["+variantAssoc+"]";
                            		logger.logError(msg);
                                    Debug.logError(gee, "Error in retrieving Product for variantAssoc ["+variantAssoc+"]", module);
                                }
                                
                                if(variantProduct == null) 
                                {
                                	String msg = "Variant product is null. Skip and continue ["+variantProduct+"]";
                            		logger.logError(msg);
                                    Debug.logWarning("Variant product is null. Skip and continue", module);
                                    continue;
                                }
                                
                                if(!variants.contains(variantProduct)) 
                                {
                                    variants.add(variantProduct);
                                }
                                
                                //stock status for variants
                                Map<String, Boolean> productStockStatusMap = getVariantProductStockStatus(variants, productStoreId, delegator, dispatcher);
                                
                                //Item id (M)
                                String variantProductId = (String) variantAssoc.get("productIdTo");
                                
                                //Item description (M)
                                String itemDescription = (pcw.get("LONG_DESCRIPTION", "html")).toString();
                                
                                if(itemDescription.isEmpty()) 
                                {
                                    itemDescription = (pcw.get("PRODUCT_NAME", "html")).toString();
                                }
                                
                                //Item main image (M)
                                String largeImageUrl = (String) parentProduct.get("largeImageUrl");
                                String image_link = siteBaseUrl + largeImageUrl;
                                
                                //Item link (M)
                                String alternativeUrl = ProductContentWrapper.getProductContentAsText(parentProduct, "ALTERNATIVE_URL", feedLocale, dispatcher, "html");
                                String productName = ProductContentWrapper.getProductContentAsText(parentProduct, "PRODUCT_NAME", feedLocale, dispatcher, "html");
                                String productUri = "";

                                if (alternativeUrl != null && !alternativeUrl.isEmpty()) 
                                {
                                    alternativeUrl = UrlServletHelper.invalidCharacter(alternativeUrl);
                                    productUri = alternativeUrl + "-" + parentProductId + "-p";
                                
                                } else {
                                    //use the product name
                                    productName = UrlServletHelper.invalidCharacter(productName);
                                    productUri = productName + "-" + parentProductId + "-p";

                                }
                                
                                String productLink = "";

                                if(!siteBaseUrl.endsWith(File.separator)) 
                                {
                                    productLink = siteBaseUrl + File.separator + webapp + File.separator + productUri;
                                
                                }else{
                                    
                                	productLink = siteBaseUrl + webapp + File.separator + productUri;
                                }
                                
                                //Item stock status (M)
                                Boolean inStock = productStockStatusMap.get(variantProductId);
                                
                                String stockStatus = "";
                                
                                if(inStock) 
                                {
                                    stockStatus = IN_STOCK_STR;
                                
                                }else{
                                    
                                	stockStatus = OUTOF_STOCK_STR;
                                }
                                
                                //Item type (M)
                                //Get primary category id from product
                                String productCategoryId = "";
                                
                                String productType = "";
                                
                                if(parentProduct.get("primaryProductCategoryId") != null) 
                                {
                                    productCategoryId = (String) parentProduct.get("primaryProductCategoryId");

                                    logger.logInfo("************* productCategoryId: "+productCategoryId);
                                
                                    GenericValue productCategory = null;

                                    try 
                                    {
                                        productCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", productCategoryId), false);
                                    
                                    }catch(GenericEntityException gee) {
                                        
                                    	Debug.logError(gee, "Error in retrieving product category with id ["+productCategoryId+"].", module);
                                    }

                                    if(productCategory != null) 
                                    {

                                        CategoryContentWrapper ccw = new CategoryContentWrapper(dispatcher, productCategory, feedLocale, "text/html");

                                        String categoryName = "";

                                        categoryName = (ccw.get("CATEGORY_NAME", "html")).toString();

                                        if(categoryName == null) {
                                            categoryName = CategoryContentWrapper.getProductCategoryContentAsText(productCategory, "CATEGORY_NAME", feedLocale, dispatcher, "html");
                                        }

                                        if(categoryName!= null && !categoryName.isEmpty()) 
                                        {
                                            productType = categoryName;
                                        
                                        }else{
                                            
                                        	productType="-";
                                        }
                                    }

                                }else{
                                    
                                	productType="-";
                                }
                                
                                //Item list price (M)
                                BigDecimal listPriceScale = BigDecimal.ZERO;
                                BigDecimal priceScale = BigDecimal.ZERO;
                                
                                HashMap<String, Object> productPriceCtx = new HashMap<>();

                                productPriceCtx.put("product", variantProduct);
                                productPriceCtx.put("currencyUomId", storeCurrencyUomId);
                                
                                if(!storeWebSiteId.isEmpty()) 
                                {
                                    productPriceCtx.put("webSiteId", storeWebSiteId);

                                }

                                productPriceCtx.put("productStoreId", productStoreId);
                                productPriceCtx.put("login.username", loginUsername);
                                productPriceCtx.put("login.password", loginPassword);

                                Map<String, Object> priceResultOut = null;

                                try 
                                {
                                    priceResultOut = dispatcher.runSync("calculateProductPrice", productPriceCtx);
                                    
                                    logger.logInfo("************* priceResultOut: "+priceResultOut);
                                    
                                
                                }catch(GenericServiceException gse) {
                                    
                                	Debug.logError(gse, "Error in calculating prices for product ["+variantProductId+"], store ["+productStoreId+"], currency ["+storeCurrencyUomId+"]", module);
                                }

                                //Get prices
                                if(priceResultOut!= null && ServiceUtil.isSuccess(priceResultOut)) 
                                {
                                	
                                    boolean validPriceFound = (Boolean) priceResultOut.get("validPriceFound");

                                    if(validPriceFound) 
                                    {
                                        BigDecimal listPrice = (BigDecimal) priceResultOut.get("listPrice");
                                        
                                        listPriceScale = listPrice.setScale(2, RoundingMode.HALF_UP);

                                        logger.logInfo("************* listPriceScale: "+listPriceScale);
                                        
                                        BigDecimal price = (BigDecimal) priceResultOut.get("specialPromoPrice"); //this is the final price
                                        
                                        if(price != null)
                                        {
                                        	priceScale = price.setScale(2, RoundingMode.HALF_UP);
                                        
                                        }else{
                                            
                                            priceScale = (BigDecimal) priceResultOut.get("price");
                                            
                                            logger.logInfo("************* priceScale: "+priceScale);
                                            
                                            priceScale = priceScale.setScale(2, RoundingMode.HALF_UP);

                                            logger.logInfo("************* priceScale last: "+priceScale);
                                        }
                                        
                                    }
                                }

                                String 	listPriceCurrency = listPriceScale.toPlainString() + " " + storeCurrencyUomId;
                                
                                String	salePriceCurrency =  priceScale.toPlainString() + " " + storeCurrencyUomId;
                                
                                logger.logInfo("************* listPriceCurrency: "+listPriceCurrency);

                                logger.logInfo("************* salePriceCurrency: "+salePriceCurrency);
                                
                                //Good Identification Number (R)
                                String goodIdentNum = getProductGoodIdentificationNumber(variantProductId, EAN_GOOD_IDENT_TYPE, delegator);

                                //Product composition (R)
                                //String productComposition = (pcw.get("INGREDIENTS")).toString();
                                
                                
                                Element item = createItem(doc, productIdTo, parentProductId, goodIdentNum, IDENTIFIER_EXISTS, productName, itemDescription, brand, google_product_category, productType, image_link, productLink, listPriceCurrency, salePriceCurrency, PRODUCT_NEW_CONDITION, stockStatus, shippingEstimateMap);
                                el_channel.appendChild(item);
                                
                            }
                            
                    	
                    	}    
                    }
                        
                    el_rss.appendChild(el_channel);
                    doc.appendChild(el_rss);

                    File output = new File(feedFileOutput);

                    saveDocument(doc, new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));

                    return ServiceUtil.returnSuccess();          
			 
			 } catch (GenericEntityException ex) {
	               	Logger.getLogger(MpGoogleFeed.class.getName()).log(Level.SEVERE, null, ex);
	                return ServiceUtil.returnError(ex.getMessage());
			 } catch (UnsupportedEncodingException ex) {
	                Logger.getLogger(MpGoogleFeed.class.getName()).log(Level.SEVERE, null, ex);
	                return ServiceUtil.returnError(ex.getMessage());
	         } catch (FileNotFoundException ex) {
	                Logger.getLogger(MpGoogleFeed.class.getName()).log(Level.SEVERE, null, ex);
	                return ServiceUtil.returnError(ex.getMessage());
	         }
		 }
		
		return ServiceUtil.returnError("@@@ Attenzione: documento non creato! @@@");
	}
	
	 public Document createDocument() 
	 {
		 DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	     dbf.setNamespaceAware(true);
	     dbf.setValidating(false);
	     try 
	     {
	    	 DocumentBuilder db = dbf.newDocumentBuilder();
	         return db.newDocument();
	     } catch (ParserConfigurationException ex) {
	         //Logger.getLogger(CatalogoGoogle.class.getName()).log(Level.SEVERE, null, ex);
	     }
	        return null;
	 }

    public void saveDocument(Document d, Writer w) {
        DOMImplementation di = d.getImplementation();
        DOMImplementationLS dils = (DOMImplementationLS) di;

        LSSerializer lss = dils.createLSSerializer();
        LSOutput lso = dils.createLSOutput();

        lso.setCharacterStream(w);
        lso.setEncoding("UTF-8");
        lss.getDomConfig().setParameter("format-pretty-print", true);

        lss.write(d, lso);

    }
    
    public Element createItem(Document d, String productIdTo, String parentProductId, String goodIdentNum, String identifier_exists, String productName, String itemDescription, String brand, String google_product_category, String productType, String image_link, String productLink,
            String price, String sale_price, String condition, String availability, Map<String, String> shippingEstimateMap) 
    {
    	Element item, shipping, el_temp;

        item = d.createElement("item");

        el_temp = d.createElement("g:id");
        el_temp.setTextContent(productIdTo);
        item.appendChild(el_temp);

        el_temp = d.createElement("g:item_group_id");
        el_temp.setTextContent(parentProductId);
        item.appendChild(el_temp);

        el_temp = d.createElement("g:mpn");
        el_temp.setTextContent(goodIdentNum);
        item.appendChild(el_temp);

        el_temp = d.createElement("g:identifier_exists");
        el_temp.setTextContent(identifier_exists);
        item.appendChild(el_temp);
        
        el_temp = d.createElement("title");
        el_temp.setTextContent(brand +"-"+ productName.toLowerCase());
        item.appendChild(el_temp);
        
        el_temp = d.createElement("description");
        el_temp.setTextContent(itemDescription);
        item.appendChild(el_temp);
        
        el_temp = d.createElement("g:brand");
        el_temp.setTextContent(brand);
        item.appendChild(el_temp);
        
        el_temp = d.createElement("g:google_product_category");
        el_temp.setTextContent(google_product_category);
        item.appendChild(el_temp);
        
        el_temp = d.createElement("g:product_type");
        el_temp.setTextContent(productType);
        item.appendChild(el_temp);

        el_temp = d.createElement("g:image_link");
        el_temp.setTextContent(image_link);
        item.appendChild(el_temp);

        el_temp = d.createElement("link");
        el_temp.setTextContent(productLink);
        item.appendChild(el_temp);
        
        el_temp = d.createElement("g:price");
        el_temp.setTextContent(price);
        item.appendChild(el_temp);
        
        if(!sale_price.equals(price))
        {
            el_temp = d.createElement("g:sale_price");
            el_temp.setTextContent(sale_price);
            item.appendChild(el_temp);
        }
        
        el_temp = d.createElement("g:condition");
        el_temp.setTextContent(condition);
        item.appendChild(el_temp);
        
        el_temp = d.createElement("g:availability");
        el_temp.setTextContent(availability);
        item.appendChild(el_temp);
        
        
        for (Entry<String,String> entry : shippingEstimateMap.entrySet())
        {
        	shipping = d.createElement("g:shipping");
        	
        	el_temp = d.createElement("g:country");
            el_temp.setTextContent(entry.getKey());
            shipping.appendChild(el_temp);
	
        	el_temp = d.createElement("g:price");
            el_temp.setTextContent(entry.getValue());
            shipping.appendChild(el_temp);
            
            item.appendChild(shipping);
        }

        return item;

    }
    
    
    /**
     * 
     * @param variantProductList
     * @param productStoreId
     * @param delegator
     * @param dispatcher
     * @return 
     */
    public Map<String, Boolean> getVariantProductStockStatus(List<GenericValue> variantProductList, String productStoreId,  Delegator delegator, LocalDispatcher dispatcher) {
        
        Map<String, Boolean> stockStatusMap = new HashMap<>();
        
        if(UtilValidate.isEmpty(variantProductList)) {
            Debug.logInfo("Variant product list is empty. Nothing to check. Returning null.", module);
            return null;
        }
        
        //Retrieve a list of all the facilities related to the store
        List<GenericValue> productStoreFacilities = null;
        
        try {
            
            EntityCondition storeFacCond = EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, productStoreId);
            productStoreFacilities = delegator.findList("ProductStoreFacility", storeFacCond, null, null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee, "Error in retrieving Store Facilities for product store ["+productStoreId+"].", module);
            return null;
        }
        
        //Filter out dated facilities
        productStoreFacilities = EntityUtil.filterByDate(productStoreFacilities);
        
        //if no facilities are available return a map with stock status of FALSE for all products.
        if(UtilValidate.isEmpty(productStoreFacilities)) {
            for(GenericValue variantProduct : variantProductList) {
                
                String _feedProductId = (String) variantProduct.get("productId");
                stockStatusMap.put(_feedProductId, Boolean.FALSE);
            }
            
            return stockStatusMap;
            
        }
        
        //For each product variant calc the avilabilities  in all the facilities
        //Boolean in_stock = false;

        
        for(GenericValue variantProduct : variantProductList) {
            
            BigDecimal variantAtpTotalAllFacilities = BigDecimal.ZERO;
            String _variantProductId = (String) variantProduct.get("productId");
            
            //Calculate the ATP for each variant in each facility
            for(GenericValue storeFacility : productStoreFacilities) {
                
                String _facilityId = (String) storeFacility.get("facilityId");

                BigDecimal variantAtp = BigDecimal.ZERO;

                try {

                    Map<String, Object> invAvailCtx = UtilMisc.toMap("productId", _variantProductId, "facilityId", _facilityId);
                    Map<String, Object> invAvailResultOutput = dispatcher.runSync("getInventoryAvailableByFacility", invAvailCtx);

                    if(ServiceUtil.isSuccess(invAvailResultOutput)) {
                        variantAtp = (BigDecimal) invAvailResultOutput.get("availableToPromiseTotal");
                    }

                }catch(GenericServiceException gse) {
                    Debug.logError(gse, "Error in running service [getInventoryAvailableByFacility] for product ["+_variantProductId+"[ and facility ["+_facilityId+"].");
                }

                //atpTotalSingleFacility = atpTotalSingleFacility.add(variantAtp);

                variantAtpTotalAllFacilities = variantAtpTotalAllFacilities.add(variantAtp);

            }
                
            
            
            //Evaluate atp total and set stock status: TRUE in-stck / FALSE: out of stock
            if(variantAtpTotalAllFacilities.compareTo(BigDecimal.ZERO) == 1) {
                stockStatusMap.put(_variantProductId, Boolean.TRUE);
            }else{
                stockStatusMap.put(_variantProductId, Boolean.FALSE);
            }
            
            
        }//end loop products


        return stockStatusMap;
        
    }
    
    
    /**
     * 
     * @param productId
     * @param goodIdentificationTypeId
     * @param delegator
     * @return 
     */
    public String getProductGoodIdentificationNumber(String productId, String goodIdentificationTypeId, Delegator delegator) {
        
        String goodIdentificationNumber = null;
        
        if(productId == null) {
            Debug.logError("Product Id is null. Cannot retrieve Good Identification Number.", module);
            return null;
        }
        
        if(goodIdentificationTypeId == null) {
            Debug.logError("Good Identification Type is null. Cannot retrieve Good Identification Number.", module);
            return null;
        }
        
        GenericValue goodIdentNum = null;
        
        try {
            goodIdentNum = delegator.findOne("GoodIdentification", UtilMisc.toMap("goodIdentificationTypeId", goodIdentificationTypeId, "productId", productId), false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee, "Error in retrieving GoodIdentificationNumber for product Id ["+productId+"] and good ident. type ["+goodIdentificationTypeId+"]", module);
            
        }
        
        if(goodIdentNum != null) {
            goodIdentificationNumber = (String) goodIdentNum.get("idValue");
        } 
           
        
        return goodIdentificationNumber;
        
    }
    
}
