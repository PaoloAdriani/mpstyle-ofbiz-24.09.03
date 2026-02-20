package mpstyle.facebook;


import mpstyle.feed.channable.ChannableWorker;
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
import org.apache.ofbiz.product.category.CategoryContentWrapper;
import org.apache.ofbiz.product.product.ProductContentWrapper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MpFacebookCatalog 
{
	public static final String module = MpFacebookCatalog.class.getName();
	
	private final static String DEFAULT_LOCALE = "it_IT";
	private final static String DEFAULT_CURRENCY_UOM = "EUR";
	private final static String IN_STOCK_STR = "in stock";
	private final static String OUTOF_STOCK_STR = "out of stock";


	public Map<String, Object> createFacebookCatalogCsv(DispatchContext ctx, Map<String, ? extends Object> context)
	{
		Delegator delegator = ctx.getDelegator();
		LocalDispatcher dispatcher = ctx.getDispatcher();

		String fbdirout = (String) context.get("fbdirout");
		
		String siteBaseUrl = (String) context.get("siteBaseUrl");
		
		String brand = (String) context.get("brand");
		
		String filenamePrefix = (String) context.get("filenamePrefix");
		
		Boolean excludeOutOfSupportProds = (Boolean) context.get("excludeOutOfSupportProducts");
		
		String productCatalogId = (String) context.get("productCatalogId");
		
		String googleProductCategoryId = (String) context.get("googleProductCategoryId");
		
		String productStoreId = (String) context.get("productStoreId");
		
		String logfilename = (String) context.get("logfilename");

		String webappDefault = (String) context.get("webappDefault");
		
		Debug.logWarning("* fbdirout: "+fbdirout, module);
		Debug.logWarning("* siteBaseUrl: "+siteBaseUrl, module);
		Debug.logWarning("* brand: "+brand, module);
		Debug.logWarning("* filenamePrefix: "+filenamePrefix, module);
		Debug.logWarning("* excludeOutOfSupportProds: "+excludeOutOfSupportProds, module);
		Debug.logWarning("* productCatalogId: "+productCatalogId, module);
		Debug.logWarning("* googleProductCategoryId: "+googleProductCategoryId, module);
		Debug.logWarning("* productStoreId: "+productStoreId, module);
		Debug.logWarning("* logfilename: "+logfilename, module);
		Debug.logWarning("* webappDefault: "+webappDefault, module);
		
		FeedLogger logger = new FeedLogger(delegator.getDelegatorTenantId(), logfilename);

		Locale feedLocale = null;

		String filename = "FB_" + filenamePrefix + "_" + "PRODUCT_FEED.csv";
		
		List<FacebookItem> facebookItemList = new ArrayList<>();

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
		
		List<GenericValue> shipEstList;
		try 
		{
			shipEstList = delegator.findList("ShipmentCostEstimate", condition, null, null, null, false);
			
			for (GenericValue shipEst : shipEstList) 
			{
	    		String geoIdTo = (String) shipEst.get("geoIdTo");
	    
	    		String geoCode = null;
	    
	    		GenericValue geoGenVal;
				try 
				{
					geoGenVal = delegator.findOne("Geo", UtilMisc.toMap("geoId", geoIdTo), false);
				
					if(geoGenVal != null)
		    		{
		    			geoCode = (String)geoGenVal.get("geoCode");
		    		}
		    
		    		BigDecimal shipEstimate = (BigDecimal) shipEst.get("orderFlatPrice");
		    
		    		String shipEstimateStr = shipEstimate.toPlainString() + " " +storeCurrencyUomId;
		    
		    		shippingEstimateMap.putIfAbsent(geoCode, shipEstimateStr);
		   	
				} catch (GenericEntityException e) {
					String msg = "Error in retrieving Geo with geoId ["+geoIdTo+"]. Quit.";
					logger.logError(msg);
					Debug.logError(e, msg, module);
					return ServiceUtil.returnError(msg);
					
				}
	   
			}
			
		} catch (GenericEntityException e1) {
			String msg = "Error in retrieving ShipmentCostEstimate with this condition ["+condition+"]. Quit.";
			logger.logError(msg);
			Debug.logError(e1, msg, module);
			return ServiceUtil.returnError(msg);
			
		}
        
		logger.logInfo("************* shippingEstimateMap: "+UtilMisc.printMap(shippingEstimateMap));
		
		List<GenericValue> catalogCategoryList = null;

		//Retrieve the "Other Search" type category/ies for the catalog
 		EntityCondition pcctCond = EntityCondition.makeCondition(EntityOperator.AND,
 		EntityCondition.makeCondition("prodCatalogId", EntityOperator.EQUALS, productCatalogId),
 		EntityCondition.makeCondition("prodCatalogCategoryTypeId", EntityOperator.EQUALS, "PCCT_OTHER_SEARCH"));

		try 
		{
			catalogCategoryList = delegator.findList("ProdCatalogCategory", pcctCond, null, null, null, false);
		
		} catch (GenericEntityException gee) {
			String msg = "Error in retrieving ProdCatalogCategory for catalog id ["+productCatalogId+"]. Quit.";
			logger.logError(msg);
			Debug.logError(gee, msg, module);
			return ServiceUtil.returnError(msg);
		}
		
		//Exclude inactive categories
		catalogCategoryList = EntityUtil.filterByDate(catalogCategoryList);
		
	
		if(UtilValidate.isEmpty(catalogCategoryList)) 
		{
			String msg = "No categories PCCT_OTHER_SEARCH found associated to the catalog id ["+productCatalogId+"]. Quit.";
			logger.logError(msg);
			return ServiceUtil.returnError(msg);
		}

		List<GenericValue> feedProductList = new ArrayList<>();

		//Add the product from each category
		for(GenericValue catalogCategory : catalogCategoryList) 
		{
    		String _productCategoryId = (String) catalogCategory.get("productCategoryId");
    		    		
			List<GenericValue> _categoryMemberList = null;
    		
			try 
			{
        		EntityCondition pcmCond = EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, _productCategoryId);
        		
				_categoryMemberList = delegator.findList("ProductCategoryMember", pcmCond, null, null, null, false);
    		
			}catch(GenericEntityException gee) {
        		String msg = "Error in retrieving ProductCategoryMember for product catalog category with id ["+_productCategoryId+"]. Quit.";
        		logger.logError(msg);
        		Debug.logError(gee, msg, module);
        		return ServiceUtil.returnError(msg);
    		}
    		
			_categoryMemberList = EntityUtil.filterByDate(_categoryMemberList);

			for(GenericValue _catMember : _categoryMemberList) 
			{
				GenericValue _product = null;
				try 
				{
					_product = _catMember.getRelatedOne("Product", false);
					
				} catch (GenericEntityException gee) {
					String msg = "Error in retrieving related Product entity for product category member with id ["+_catMember.getString("productId")+"]. Quit.";	
					logger.logError(msg);
					Debug.logError(gee, msg, module);
					return ServiceUtil.returnError(msg);
				}

				if(!feedProductList.contains(_product)) 
				{
					feedProductList.add(_product);
				}
			}
		}	

		if(UtilValidate.isEmpty(feedProductList)) 
		{
			String msg = "No products found in Channable catalog and categories. Cannot build feed.";
			logger.logError(msg);
			Debug.logError(msg, module);
			return ServiceUtil.returnError(msg);
		}
					
		//Filtering products by out of support date
		if(excludeOutOfSupportProds) 
		{
			feedProductList = ChannableWorker.filterOutOfSupportProducts(feedProductList, delegator);
		}

		Map<String, Boolean> productStockStatusMap = ChannableWorker.getParentProductStockStatus(feedProductList, productStoreId, delegator, dispatcher);

		for(GenericValue feedProduct : feedProductList) 
		{
			ProductContentWrapper pcw = new ProductContentWrapper(dispatcher, feedProduct, feedLocale,"text/html");

			String productId = (String) feedProduct.get("productId");
			
			String title = pcw.get("PRODUCT_NAME", "html").toString();

			String description = pcw.get("DESCRIPTION", "html").toString();

			String largeImageUrl = (String) feedProduct.get("largeImageUrl");
 			
			String imageLink = siteBaseUrl + largeImageUrl;
 
			System.out.println("*********************imageLink: "+imageLink);

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
				productLink = siteBaseUrl + File.separator + webappDefault + File.separator + productUri;
			}else{
				productLink = siteBaseUrl + webappDefault + File.separator + productUri;
			}

			System.out.println("*********************productLink: "+productLink);
			
			//Item stock status (M)
			Boolean inStock = productStockStatusMap.get(productId);
			String stockStatus = "";
			if(inStock) 
			{
    			stockStatus = IN_STOCK_STR;
			
			}else{
    				
				stockStatus = OUTOF_STOCK_STR;
			}

			String productCategoryId = "";
			String productType = "";
				
			if(feedProduct.get("primaryProductCategoryId") != null) 
			{
    			productCategoryId = (String) feedProduct.get("primaryProductCategoryId");
    
    			GenericValue productCategory = null;
    
    			try {
    			    productCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", productCategoryId), false);
    			}catch(GenericEntityException gee) {
    			    Debug.logError(gee, "Error in retrieving product category with id ["+productCategoryId+"].", module);
    			}
    
    			if(productCategory != null) 
				{
        			CategoryContentWrapper ccw = new CategoryContentWrapper(dispatcher, productCategory, feedLocale, "text/html");
        
        			String categoryName = ccw.get("CATEGORY_NAME", "html").toString();
        
        			if(categoryName == null) 
					{
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

			logger.logInfo("************* productType: "+productType);
			
			logger.logInfo("************* productCategoryId: "+productCategoryId);
			
			//Item list price (M)
			BigDecimal listPriceScale = BigDecimal.ZERO;
			BigDecimal priceScale = BigDecimal.ZERO;
                            
			HashMap<String, Object> productPriceCtx = new HashMap<>();
			productPriceCtx.put("product", feedProduct);
			productPriceCtx.put("currencyUomId", storeCurrencyUomId);
                            
			if(!storeWebSiteId.isEmpty()) 
			{
			    productPriceCtx.put("webSiteId", storeWebSiteId);
			}
			
			productPriceCtx.put("productStoreId", productStoreId);
			productPriceCtx.put("login.username", "paolo.adriani");
			productPriceCtx.put("login.password", "paolo");

			Map<String, Object> priceResultOut = null;
			try 
			{
				priceResultOut = dispatcher.runSync("calculateProductPrice", productPriceCtx);

				logger.logInfo("************* priceResultOut: "+priceResultOut);
                
			}catch(GenericServiceException gse) {

				Debug.logError(gse, "Error in calculating prices for product ["+feedProduct+"], store ["+productStoreId+"], currency ["+storeCurrencyUomId+"]", module);
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
        
        				logger.logInfo("************* priceScale getting price: "+priceScale);
        
        				priceScale = priceScale.setScale(2, RoundingMode.HALF_UP);
        				
						logger.logInfo("************* priceScale: "+priceScale);
    				}
				}
			}
			
			String 	listPriceCurrency = listPriceScale.toPlainString() + " " + storeCurrencyUomId;
                                
			String	salePriceCurrency =  priceScale.toPlainString() + " " + storeCurrencyUomId;
                                
			logger.logInfo("************* listPriceCurrency: "+listPriceCurrency);
			logger.logInfo("************* salePriceCurrency: "+salePriceCurrency);
			
			FacebookItem fi = MpFacebookCatalogHelper.createFacebookItem(productId,title,description,imageLink,productLink,stockStatus,productType,productCategoryId,
			listPriceCurrency,salePriceCurrency, brand, googleProductCategoryId, shippingEstimateMap);

			facebookItemList.add(fi);
                    
		}

		MpFacebookCatalogHelper.createFacebookCatalogCsvFile(facebookItemList, fbdirout, filename);		
		
		return ServiceUtil.returnSuccess();
	}	
}
