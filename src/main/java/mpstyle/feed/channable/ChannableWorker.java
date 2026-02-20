/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.feed.channable;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.product.product.ProductContentWrapper;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 * @author equake58
 */
public class ChannableWorker {
    
    public static String module = ChannableWorker.class.getName();
    
    private static final String PRODUCT_ENTITY = "Product";
    
    /**
     * Filter Out Of Support products.
     * @param toFilterList
     * @param delegator
     * @return 
     */
    public static List<GenericValue> filterOutOfSupportProducts(List<GenericValue> toFilterList, Delegator delegator) {
        
        List<GenericValue> filteredList = null;
        
        if(UtilValidate.isEmpty(toFilterList)) {
            Debug.logWarning("toFilterList is null; returning empty list.", module);
            return new ArrayList<>();
        }
        
        filteredList = new ArrayList<>();
        
        for(GenericValue toFilterProd : toFilterList) {
            
            GenericValue _toCheckProduct = null;
            
            String _entityName = toFilterProd.getEntityName();
            
            //if I do not have the Product try to retrieve it. If not abort and return empty list;
            if(!_entityName.equals(PRODUCT_ENTITY)) {
                
                try {
                    _toCheckProduct = toFilterProd.getRelatedOne("Product", false);
                } catch (GenericEntityException ex) {
                    Debug.logError(ex, "Error in retrieving relation with entity Product for ["+toFilterProd+"]. Quit.", module);
                    return new ArrayList<>();
                }
                
            }else{
                _toCheckProduct = toFilterProd;
            }
            
            java.sql.Timestamp supportDiscDate = (java.sql.Timestamp) _toCheckProduct.get("supportDiscontinuationDate");

            if(supportDiscDate != null && supportDiscDate.before(UtilDateTime.nowTimestamp()) ) {
                Debug.logInfo("*** Support for this product ["+_toCheckProduct.getString("productId")+"] is finished. Skip product.", module);
                continue;
            }
            
            //Product is not out of support: keep it.
            filteredList.add(_toCheckProduct);
            
        }
        
        
        return filteredList;
        
        
    }

    /**
     * 
     * @param feedProductList
     * @param productStoreId
     * @param delegator
     * @param dispatcher
     * @return 
     */
    public static Map<String, Boolean> getParentProductStockStatus(List<GenericValue> feedProductList, String productStoreId,  Delegator delegator, LocalDispatcher dispatcher) {
        
        Map<String, Boolean> stockStatusMap = new HashMap<>();
        
        if(UtilValidate.isEmpty(feedProductList)) {
            Debug.logInfo("Feed product list is empty. Nothing to check. Returning null.", module);
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
            for(GenericValue feedProduct : feedProductList) {
                
                String _feedProductId = (String) feedProduct.get("productId");
                stockStatusMap.put(_feedProductId, Boolean.FALSE);
            }
            
            return stockStatusMap;
            
        }
        
        //For each product calc the avilabilities for each variant in all the facilities
        //Boolean in_stock = false;

        
        for(GenericValue feedProduct : feedProductList) {
            
            BigDecimal variantAtpTotalAllFacilities = BigDecimal.ZERO;
            String _feedProductId = (String) feedProduct.get("productId");
            List<GenericValue> variantAssocList = null;
            
            
            try {
                
                Map<String, Object> productVariantCtx = UtilMisc.toMap("productId", _feedProductId);
                Map<String, Object> variantResultOutput = dispatcher.runSync("getAllProductVariants", productVariantCtx);
                
                if(ServiceUtil.isSuccess(variantResultOutput)) {
                    variantAssocList = (List) variantResultOutput.get("assocProducts");
                }

            }catch(GenericServiceException gse) {
                Debug.logError(gse, "Error in running service [getAllProductVariants] for product ["+_feedProductId+"]. Skip this product.");
                continue;
            }
            
            
            if(variantAssocList!= null && UtilValidate.isNotEmpty(variantAssocList)) {
                
                //Calculate the ATP for each variant in each facility
                for(GenericValue storeFacility : productStoreFacilities) {
                    
                    BigDecimal atpTotalSingleFacility = BigDecimal.ZERO;
                    String _facilityId = (String) storeFacility.get("facilityId");
                    
                    //loop the variants
                    for(GenericValue variant : variantAssocList) {
                        
                        String _productVariantId = (String) variant.get("productIdTo");
                        BigDecimal variantAtp = BigDecimal.ZERO;
                        
                        try {
                            
                            Map<String, Object> invAvailCtx = UtilMisc.toMap("productId", _productVariantId, "facilityId", _facilityId);
                            Map<String, Object> invAvailResultOutput = dispatcher.runSync("getInventoryAvailableByFacility", invAvailCtx);
                            
                            if(ServiceUtil.isSuccess(invAvailResultOutput)) {
                                variantAtp = (BigDecimal) invAvailResultOutput.get("availableToPromiseTotal");
                            }
                            
                        }catch(GenericServiceException gse) {
                            Debug.logError(gse, "Error in running service [getInventoryAvailableByFacility] for product ["+_productVariantId+"[ and facility ["+_facilityId+"].");
                        }
                        
                        atpTotalSingleFacility = atpTotalSingleFacility.add(variantAtp);
                        
                    }
                    
                    variantAtpTotalAllFacilities = variantAtpTotalAllFacilities.add(atpTotalSingleFacility);
                    
                }
                
            }
            
            //Evaluate atp total and set stock status: TRUE in-stck / FALSE: out of stock
            if(variantAtpTotalAllFacilities.compareTo(BigDecimal.ZERO) == 1) {
                stockStatusMap.put(_feedProductId, Boolean.TRUE);
            }else{
                stockStatusMap.put(_feedProductId, Boolean.FALSE);
            }
            
            
        }//end loop products


        return stockStatusMap;
        
    }
    
    /**
     * 
     * @param variantProductList
     * @param productStoreId
     * @param delegator
     * @param dispatcher
     * @return 
     */
    public static Map<String, Boolean> getVariantProductStockStatus(List<GenericValue> variantProductList, String productStoreId,  Delegator delegator, LocalDispatcher dispatcher) {
        
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
     * @param pcw
     * @param addimg_count
     * @param siteBaseUrl
     * @return 
     */
    public static String getProductAdditionalImages(ProductContentWrapper pcw, int addimg_count, String siteBaseUrl) {
    
        StringBuilder sb = new StringBuilder();
        boolean useSiteUrl = false;
        String contentStringPrefix = "ADDITIONAL_IMAGE_";
        List<String> additionalImageUrls = new ArrayList<>();
        
        if(addimg_count < 0) {
            addimg_count = 0;
        }
        
        if(siteBaseUrl != null) {
            if(!siteBaseUrl.endsWith(File.separator)) {
                siteBaseUrl = siteBaseUrl + File.separator;
                useSiteUrl = true;
            }
        } 
        
        
        if(addimg_count > 0) {
            
            for(int i = 1; i <= addimg_count; i++) {
                
                String addImgContent = contentStringPrefix + Integer.toString(i);
                
                if(pcw.get(addImgContent, "html") != null) {
                    String _imgUrl = pcw.get(addImgContent, "html").toString();
                    if(!additionalImageUrls.contains(_imgUrl)) {
                        
                        if(useSiteUrl) {
                            _imgUrl = siteBaseUrl + _imgUrl;
                            additionalImageUrls.add(_imgUrl);
                        }else{
                            additionalImageUrls.add(_imgUrl);
                        }
                    }
                    
                    
                }
                
            }
            
            if(UtilValidate.isNotEmpty(additionalImageUrls)) {
                
                int totalUrls = additionalImageUrls.size();
                
                if(totalUrls == 1) {
                    sb.append(additionalImageUrls.get(0));
                }else{
                    int count = 0;
                    for(String _url : additionalImageUrls) {
                        
                        if(count < (totalUrls - 1)) {
                            sb.append(_url).append(",");
                        }else{
                            sb.append(_url);
                        }
                        
                    }
                }
                
            }
            
        }
        
        
        
        return sb.toString();
        
    }
    
    /**
     * 
     * @param productId
     * @param goodIdentificationTypeId
     * @param delegator
     * @return 
     */
    public static String getProductGoodIdentificationNumber(String productId, String goodIdentificationTypeId, Delegator delegator) {
        
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
    
    
    
}//end class
