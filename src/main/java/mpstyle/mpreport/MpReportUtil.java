/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.mpreport;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.order.order.OrderReadHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.List;
import java.sql.Timestamp;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 *
 * @author equake58
 */
public class MpReportUtil {
    
    public static final String module = MpReportUtil.class.getName();
    
    private final static String E_RETURN_ITEM = "ReturnItem";
    private final static String E_RETURN_REASON = "ReturnReason";
    private final static String E_RETURN_STATUS = "ReturnStatus";
    private final static String E_PRODUCT = "Product";
    private final static String E_PRODUCT_STORE = "ProductStore";
    private final static String E_PRODUCT_ASSOC = "ProductAssoc";
    private final static String E_PERSON = "Person";
    
    private final static BigDecimal HUNDRED = new BigDecimal(100);
    

    /**
     * 
     * @param delegator
     * @param orderStatus
     * @param reportFromDate
     * @param reportThruDate
     * @return 
     */
    protected static List<GenericValue> getOrderDateFilterList(Delegator delegator, String orderStatus, Timestamp reportFromDate, Timestamp reportThruDate) {
        
        List<GenericValue> orderList = null;
        
        EntityCondition orderCondition = null;
         
        EntityCondition orderStatusCondition = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, orderStatus);
         
        EntityCondition fromDateCondition = null;
        EntityCondition thruDateCondition = null;
        EntityCondition filterDateCondition = null;
         
         if(UtilValidate.isNotEmpty(reportFromDate)) {
             fromDateCondition = EntityCondition.makeCondition("entryDate", EntityOperator.GREATER_THAN_EQUAL_TO, reportFromDate);
         }
         
         if(UtilValidate.isNotEmpty(reportThruDate)) {
             thruDateCondition = EntityCondition.makeCondition("entryDate", EntityOperator.LESS_THAN_EQUAL_TO, reportThruDate);
         }
          
        if(fromDateCondition != null && thruDateCondition != null) {             
            filterDateCondition = EntityCondition.makeCondition(EntityOperator.AND, fromDateCondition, thruDateCondition);
        }else if(fromDateCondition != null) {
            filterDateCondition = fromDateCondition;
        }else if(thruDateCondition != null) {
            filterDateCondition = thruDateCondition;
        }
        
        if(filterDateCondition != null) {
            orderCondition = EntityCondition.makeCondition(EntityOperator.AND, orderStatusCondition, filterDateCondition);
        }else{
            orderCondition = orderStatusCondition;
        }
        
        //Get order list order by entryDate (asc)
        try {
         
            orderList = delegator.findList("OrderHeader", orderCondition, null, UtilMisc.toList("entryDate"), null, false);
            
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
            return null;
        }

        return orderList;

    }
    
    /**
     * 
     * @param productId - Ecommerce productId: SEASONLINEMPARTCOLOR.SZ
     * @return 
     */
    protected static String getProductSeasonFromEcomSku(String productId) {
        
        String season = null;
        
        season = productId.substring(0,2);
        
        return season;
        
    }
    
    /**
     * 
     * @param productId - Ecommerce productId: SEASONLINEMPARTCOLOR.SZ
     * @return 
     */
    protected static String getProductLineFromEcomSku(String productId) {
        
        String line = null;
        
        line = productId.substring(2,3);
        
        return line;
        
    }
    
    /**
     * 
     * @param productId - Ecommerce productId: SEASONLINEMPARTCOLOR.SZ
     * @return 
     */
    protected static String getMpCodArtFromEcomSku(String productId) {
        
        String mpart = null;
        
        mpart = productId.substring(3,9);
        
        return mpart;
        
    }
    
    /**
     * 
     * @param productId - Ecommerce productId: SEASONLINEMPARTCOLOR.SZ
     * @return 
     */
    protected static String getMpColorFromEcomSku(String productId) {
        
        String mpcolor = null;
        
        //child
        if(productId.contains(".")) {
        
            mpcolor = productId.substring(9,productId.indexOf("."));
            
        }else{ //father
            
            mpcolor = productId.substring(9);
            
        }
        
        return mpcolor;
        
    }
    
    /**
     * 
     * @param productId - Ecommerce productId: SEASONLINEMPARTCOLOR.SZ
     * @return 
     */
    protected static String getMpSizeFromEcomSku(String productId) {
        
        String size = null;
        
        //child
        if(productId.contains(".")) {
        
            size = productId.substring((productId.indexOf(".")+1));
            
        }
        
        return size;
        
    }
    
    
    /**
     * 
     * @param productId
     * @return 
     */
    protected static String getProductPrimaryCategoryId(Delegator delegator, String productId) {
        
        String primaryProductCategoryId = null;
        
        GenericValue product = null;
        
        try {
            
            product = delegator.findOne(E_PRODUCT, UtilMisc.toMap("productId", productId), false);
            
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
        }
        
        if(product != null) {
            primaryProductCategoryId = (String) product.get("primaryProductCategoryId");
        }
        
        //check if there is a parent of this product
        if(primaryProductCategoryId == null) {
            
            GenericValue parentProduct = getProductParent(delegator, productId);
            
            primaryProductCategoryId = (String) parentProduct.get("primaryProductCategoryId");
        }
        
        return primaryProductCategoryId;
        
    }
    
     /**
     * 
     * @param product
     * @return 
     */
    protected static String getProductPrimaryCategoryId(GenericValue product) {
        
        String productPrimaryCategoryId = null;
        
        
        
        
        return productPrimaryCategoryId;
        
    }
    
    
    protected static GenericValue getProductParent(Delegator delegator, String productId) {
        
        GenericValue parentProduct = null;
        
        EntityCondition assocCondition = EntityCondition.makeCondition(EntityOperator.AND,
                EntityCondition.makeCondition("productAssocTypeId", EntityOperator.EQUALS, "PRODUCT_VARIANT"),
                EntityCondition.makeCondition("productIdTo", EntityOperator.EQUALS, productId));
        
        List<GenericValue> variantAssocList = null;
        
        try {
            variantAssocList = delegator.findList(E_PRODUCT_ASSOC, assocCondition, null, null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
        }
        
        if(variantAssocList != null) {
            
            variantAssocList = EntityUtil.filterByDate(variantAssocList);
            
            GenericValue firstAssoc = EntityUtil.getFirst(variantAssocList);
            
            String parentProductId = (String) firstAssoc.get("productId");
            
            try {
                parentProduct = delegator.findOne(E_PRODUCT, UtilMisc.toMap("productId", parentProductId), false);
                
            }catch(GenericEntityException gee) {
                Debug.logError(gee, module);
            }
            
        }
        
        return parentProduct;
        
    }
    
    /**
     * Return date string from timestamp
     * @param ts
     * @return 
     */
    protected static String getStringDateFromTimestamp(Timestamp ts) {
        
    	if(ts == null || UtilValidate.isEmpty(ts)) {
            Debug.logWarning("Timestamp is null: cannot extract Year. Returning -1.", module);
            return "-1";
        }
    	
        Calendar c = Calendar.getInstance();
        
        c.setTimeInMillis(ts.getTime());
        
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1; //calendar Month starts from 0
        int day = c.get(Calendar.DAY_OF_MONTH);
        
        StringBuilder dateBuilder = new StringBuilder();
        
        dateBuilder.append(day).append("/").append(month).append("/").append(year);
        
        return dateBuilder.toString();
        
    } 
    
    /**
     * Return date-time string from timestamp
     * @param ts
     * @return 
     */
    protected static String getStringDateTimeFromTimestamp(Timestamp ts) {
        
    	if(ts == null || UtilValidate.isEmpty(ts)) {
            Debug.logWarning("Timestamp is null: cannot extract Year. Returning -1.", module);
            return "-1";
        }
    	
    	Calendar c = Calendar.getInstance();
       
        c.setTimeInMillis(ts.getTime());
        
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1; //calendar Month starts from 0
        int day = c.get(Calendar.DAY_OF_MONTH);
        
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        	
        StringBuilder dateBuilder = new StringBuilder();
        
        dateBuilder.append(day).append("/").append(month).append("/").append(year);
        
        dateBuilder.append(" ").append(hour).append(":").append(minute).append(":").append(sec);
        	
        return dateBuilder.toString();
        
    } 
    
        /**
         * Date string formatted as: YYYY_MM(dd)
         * @param ts
         * @return 
         */
        protected static String getStringDateTimeFromTimestampFormat2(Timestamp ts) {
        
        Calendar c = Calendar.getInstance();
        
        c.setTimeInMillis(ts.getTime());
        
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1; //calendar Month starts from 0
        String month_str = "";
        if(month < 10) {
            month_str = "0" + Integer.toString(month);
        }else{
            month_str = Integer.toString(month);
        }
        
        
        int day = c.get(Calendar.DAY_OF_MONTH);
        String day_str = "";
        
        if(day < 10) {
            day_str = "0" + Integer.toString(day);
        }else{
            day_str = Integer.toString(day);
        }
        
        
        StringBuilder dateBuilder = new StringBuilder();
        
        dateBuilder.append(year).append("_").append(month_str).append("(").append(day_str).append(")");
        
        return dateBuilder.toString();
        
    } 
    
    
    /**
     * Returns year and month in this format: YYYY.MM
     * @param ts
     * @return 
     */
      protected static String getStringYMFromTimestamp(Timestamp ts) {
        
    	if(ts == null || UtilValidate.isEmpty(ts)) {
           Debug.logWarning("Timestamp is null: cannot extract Year. Returning -1.", module);
           return "-1";
        }  
    	  
        Calendar c = Calendar.getInstance();
        
        c.setTimeInMillis(ts.getTime());
        
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1; //calendar Month starts from 0
        
        StringBuilder dateBuilder = new StringBuilder();
        
        dateBuilder.append(year).append(".").append(month);
        
        return dateBuilder.toString();
        
    } 
      
    /**
     * Returns Year value from a Timestamp object.
     * @param ts
     * @return 
     */
    protected static int getYearFromTimestamp(Timestamp ts) {
        
        int YYYY = -1;
        
        if(ts == null) {
            Debug.logWarning("Timestamp is null: cannot extract Year. Returning -1.", module);
            return YYYY;
        }
        
        Calendar c = Calendar.getInstance();
        
        c.setTimeInMillis(ts.getTime());
        
        YYYY = c.get(Calendar.YEAR);     
        
        return YYYY;
        
    }
    
    /**
     * Returns Month value from a Timestamp object.
     * @param ts
     * @return 
     */
    protected static int getMonthFromTimestamp(Timestamp ts) {
        
        int MM = -1;
        
        if(ts == null) {
            Debug.logWarning("Timestamp is null: cannot extract Month. Returning -1.", module);
            return MM;
        }
        
        Calendar c = Calendar.getInstance();
        
        c.setTimeInMillis(ts.getTime());
        
        MM = c.get(Calendar.MONTH) + 1; 
        
        return MM;
        
    }
    
    /**
     * Returns Day value from a Timestamp object.
     * @param ts
     * @return 
     */
    protected static int getDayFromTimestamp(Timestamp ts) {
        
        int DD = -1;
        
        if(ts == null) {
            Debug.logWarning("Timestamp is null: cannot extract Day. Returning -1.", module);
            return DD;
        }
        
        Calendar c = Calendar.getInstance();
        
        c.setTimeInMillis(ts.getTime());
        
        DD = c.get(Calendar.DAY_OF_MONTH);
        
        return DD;
        
    }
      
     /**
      * 
      * @param finalPrice
      * @param startPrice
      * @return 
      */ 
     protected static BigDecimal calcDiscount(BigDecimal finalPrice, BigDecimal startPrice) {
          
         BigDecimal discount = BigDecimal.ZERO;
         
          
          if(startPrice.compareTo(BigDecimal.ZERO) == 0) {
              return BigDecimal.ZERO;
          }
          
          //difference is negative so I round to the ceiling (ie: -49.80 => -50.00)
          discount = ( (finalPrice.subtract(startPrice)).divide(startPrice, RoundingMode.CEILING) ).multiply(HUNDRED);
          
          discount.setScale(0, RoundingMode.FLOOR);
          
         return discount;
         
      }
     
     /**
      * 
      * @param delegator
      * @param returnId
      * @param returnStatusId
      * @return 
      */
     protected static String getReturnChangeStatusDateString(Delegator delegator, String returnId, String returnStatusId) {
         
         String returnCompletedDate = null;
         
         List<GenericValue> returnStatusList = null;
                            
        try {

            EntityCondition retStatusCond = EntityCondition.makeCondition(EntityOperator.AND,
                    EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, returnId),
                    EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, returnStatusId));

            returnStatusList = delegator.findList(E_RETURN_STATUS, retStatusCond, null, null, null, false);

        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
        }
                            
        if(returnStatusList != null && returnStatusList.size() > 0) {

            Timestamp completedTs = null;

            GenericValue statusCompleted = EntityUtil.getFirst(returnStatusList);

            completedTs = (Timestamp) statusCompleted.get("statusDatetime");

            returnCompletedDate = getStringDateFromTimestamp(completedTs);

        } else {
            
            returnCompletedDate = "RESO APERTO";
            
        }
      
         return returnCompletedDate;
         
     }
     
     /**
      * 
      * @param orderStatusList
      * @param orderStatus
      * @return 
      */
     protected static Timestamp getReturnStatusChangeDate(List<GenericValue> returnStatusList, String returnStatus) {
         
        Timestamp returnStatusChangeTs = null;
         
        if(returnStatus == null || returnStatus.isEmpty()) {
            Debug.logWarning("No return status to search for. Returning null.", module);
            return null;
        }
        
        if(returnStatusList == null || returnStatusList.isEmpty()) {
            Debug.logWarning("No order status list to loop. Returning null.", module);
            return null;
        }
         
         
        for(GenericValue retStatus : returnStatusList) {
             
            if( ( (String)retStatus.get("statusId") ).equals(returnStatus) ) {
                 
                 returnStatusChangeTs = (Timestamp) retStatus.get("statusDatetime");
                 
            }
             
        }
        
        if(returnStatusChangeTs == null) {
            Debug.logWarning("Change date for status [" + returnStatus + "] not found. Returning null.", module);
        }
         
         
         return returnStatusChangeTs;
         
         
         
     }
     
     /**
      * 
      * @param delgator
      * @param returnId
      * @return 
      */
     protected static List<GenericValue> getReturnStatusList(Delegator delegator, String returnId) {
         
         List<GenericValue> returnStatusList = null;
         
         if(UtilValidate.isEmpty(returnId)) {
             Debug.logWarning("Return Id is null. Cannot retrieve return status list", module);
             return null;
         }
         
         EntityCondition returnStatusCondition = EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, returnId);
         
         try {
             
             returnStatusList = delegator.findList("ReturnStatus", returnStatusCondition, null, null, null, false);
             
         }catch(GenericEntityException gee) {
             Debug.logError(gee, module);
         }
         
         
         return returnStatusList;
         
     }
     
     /**
      * 
      * @param delegator
      * @param returnReasonId
      * @return 
      */
     protected static String getReturnItemReasonDescription(Delegator delegator, String returnReasonId) {
         
         String reasonDescription = null;
         
         GenericValue returnReason = null;
         
         try {
            returnReason = delegator.findOne(E_RETURN_REASON, UtilMisc.toMap("returnReasonId", returnReasonId), false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
        }
                        
        if(returnReason != null) {
            reasonDescription = (String) returnReason.get("description");
        }else{
            reasonDescription = "-";
        }
         
         
         return reasonDescription;
         
         
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static BigDecimal getReturnTotalQuantity(List<GenericValue> returnItemList) {
         
         BigDecimal returnTotQty = BigDecimal.ZERO;
         
         if(returnItemList == null) {
             Debug.logWarning("Retunr item list is null: cannot calc total return quantity.", module);
             return BigDecimal.ZERO;
         }
         
         EntityCondition filterCondition = EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED");
        
        returnItemList = EntityUtil.filterByCondition(returnItemList, filterCondition);
         
         
         for(GenericValue returnItem : returnItemList) {
             
             BigDecimal qty = (BigDecimal) returnItem.get("returnQuantity");
             
             returnTotQty = returnTotQty.add(qty); 
             
         }
         
         return returnTotQty;
         
         
     }
     
     
     /**
      * 
      * @param delegator
      * @param orderId
      * @param orderItemSeqId
      * @return 
      */
     protected static List<GenericValue> getOrderItmReturnItemList(Delegator delegator, String orderId, String orderItemSeqId) {
         
         List<GenericValue> returnItemList = null;
         
        try {
                    
            EntityCondition returnItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                    EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId),
                    EntityCondition.makeCondition("orderItemSeqId", EntityOperator.EQUALS, orderItemSeqId));
                    
                    
            returnItemList = delegator.findList(E_RETURN_ITEM, returnItemCondition, null, UtilMisc.toList("lastUpdatedStamp DESC"), null, false);
                    
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
        }
         
         
         return returnItemList;
         
         
     }
     
     
     /**
      * 
      * @param orderStatusList
      * @param orderStatus
      * @return 
      */
     protected static Timestamp getOrderStatusChangeDate(List<GenericValue> orderStatusList, String orderStatus) {
         
        Timestamp orderStatusChangeTs = null;
         
        if(orderStatus == null || orderStatus.isEmpty()) {
            Debug.logWarning("No order status to search for. Returning null.", module);
            return null;
        }
        
        if(orderStatusList == null || orderStatusList.isEmpty()) {
            Debug.logWarning("No order status list to loop. Returning null.", module);
            return null;
        }
         
         
        for(GenericValue ordStatus : orderStatusList) {
             
            if( ( (String)ordStatus.get("statusId") ).equals(orderStatus) ) {
                 
                 orderStatusChangeTs = (Timestamp) ordStatus.get("statusDatetime");
                 
            }
             
        }
        
        if(orderStatusChangeTs == null) {
            Debug.logWarning("Change date for status [" + orderStatus + "] not found. Returning null.", module);
        }
         
         
         return orderStatusChangeTs;
         
         
         
     }
     
     /**
      * 
      * @param receivedTs
      * @param shippedTs
      * @return 
      */
     protected static int orderReceivedShippedDaysDiff(Timestamp receivedTs, Timestamp shippedTs) {
         
         Long diff_days_long = new Long(-1);
         
        if(receivedTs == null || shippedTs == null) {
            Debug.logWarning("One or both of received/shipped order timestamp is null: cannot calculate difference.", module);
            return diff_days_long.intValue();
            
        }
         
        long millisec_diff = shippedTs.getTime() - receivedTs.getTime();
        
        
        if(millisec_diff > 0) {
            
            diff_days_long = ( millisec_diff / (1000 * 60 * 60 * 24) );
            
            
        }else if(millisec_diff == 0) {
            diff_days_long = 0L;
            return diff_days_long.intValue();
        }else {
            //..difference is negative
        }
         
        return diff_days_long.intValue();
         
     }
     
     /**
      * 
      * @param delegator
      * @param partyId
      * @return 
      */
     protected static String getPartyFirstLastName(Delegator delegator, String partyId) {
         
         String party_name = null;
         
         if(UtilValidate.isNotEmpty(partyId)) {
             
             GenericValue person = null;
             
             try {
                 
                person = delegator.findOne(E_PERSON, UtilMisc.toMap("partyId", partyId), false);
                
             }catch(GenericEntityException gee) {
                 Debug.logError(gee, module);
             }
             
             if(person != null) {
                 
                party_name = ((String) person.get("firstName")) + " " + ((String) person.get("lastName"));
                 
             }else{
                 party_name = "-";
             }
             
         }else{
             party_name = "-";
         }
         
         return party_name;
         
     }
     
     /**
      * 
      * @param productStoreId
      * @return storeName
      */
     protected static String getProductStoreName(Delegator delegator, String productStoreId) {
         
         String storeName = null;
         
         if(UtilValidate.isEmpty(productStoreId)) {
             
             storeName = "-";
             
         }else{
             
             GenericValue productStore = null;
             
             try {
                 productStore = delegator.findOne(E_PRODUCT_STORE, UtilMisc.toMap("productStoreId", productStoreId), false);
             }catch(GenericEntityException gee) {
                 Debug.logError(gee, module);
             }
             
             if(productStore != null) {
                 
                 storeName = (String) productStore.get("storeName");
                 
             }else{
                 storeName = "-";
             }
             
             
         }
         
         
         return storeName;
         
     }
     
     /**
      * 
      * @param orderPaymentPreferenceList
      * @return 
      */
     protected static String getOrderPaymentMethod(List<GenericValue> orderPaymentPreferenceList) {
         
         String paymentMethod = null;
         
         if(orderPaymentPreferenceList != null) {
                
            
                
            for(GenericValue payment : orderPaymentPreferenceList) {
                    
                String paymentMethodType = (String) payment.get("paymentMethodTypeId");
                    
                if(UtilValidate.isNotEmpty(paymentMethodType)) {
                    
                    if(paymentMethodType.startsWith("EXT_")) {
                        paymentMethod = (paymentMethodType.substring("EXT_".length())).toUpperCase();

                    }
                        
                }else{
                    paymentMethod = "-";
                }
                    
                    paymentMethod = paymentMethod + " ";
                }
                
                
                
            }else{
                
                paymentMethod = "-";
                
            }
         
         return paymentMethod;
         
         
     }
     
     /**
      * 
      * @param orderAdjustmentList
      * @param orderAdjustmentTypeId
      * @param freeShippingPromos
      * @return 
      */
     protected static BigDecimal getOrderShippingCharges(List<GenericValue> orderAdjustmentList, String orderAdjustmentTypeId, String freeShippingPromos) {
         
         BigDecimal shipping_charges = BigDecimal.ZERO;
         
         
         if(orderAdjustmentList == null) {
             Debug.logWarning("Order Adjustment List is null: cannot get order shipping charges.", module);
             return BigDecimal.ZERO;
         }
         
         
        EntityCondition filterCondition = EntityCondition.makeCondition("orderAdjustmentTypeId", EntityOperator.EQUALS, orderAdjustmentTypeId);
         
        orderAdjustmentList = EntityUtil.filterByCondition(orderAdjustmentList, filterCondition);
        
        
        if(orderAdjustmentTypeId.equals("SHIPPING_CHARGES"))
        {
            // sto recuperando SOLO le SHIPPING CHARGES 
            for(GenericValue orderAdjustment : orderAdjustmentList) 
            {
                shipping_charges = shipping_charges.add((BigDecimal)orderAdjustment.get("amount"));
             
            }
        
        
        }else{
            
            // considero le promotion_adjustment che hanno la promo con il free shipping.
            
            String[] freshipPromo = null;
            
            if (freeShippingPromos.contains(","))
            {
                freshipPromo = freeShippingPromos.split(",");
            }
            
            for(GenericValue orderAdjustment : orderAdjustmentList) 
            {
                String productPromoId = orderAdjustment.getString("productPromoId");
                
                if(UtilValidate.isNotEmpty(productPromoId))
                {	
	                for (int i = 0; i < freshipPromo.length; i++)
	                {
	                    String promoFreeShip = freshipPromo[i].trim();
	                
	                    if(productPromoId.equals(promoFreeShip))
	                    {
	                    	
	                    	BigDecimal amount = (BigDecimal) orderAdjustment.get("amount");
	                    	
	                    	if(amount == null)
	                    	{
	                    		amount = BigDecimal.ZERO;
	                    	}
	                    	
	                        shipping_charges = shipping_charges.add(amount);
	                    }
	                }
                }
             
            }
            
        }
         
        return shipping_charges.setScale(0, RoundingMode.UNNECESSARY);
        //return shipping_charges;
         
         
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static boolean hasOrderReturn(List<GenericValue> returnItemList) {
         
        boolean has_order_return = false;
         
        if(returnItemList == null) {
             Debug.logWarning("No return item list provided. Returning false", module);
             return false;
        }
         
        EntityCondition filterCondition = EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED");

        returnItemList = EntityUtil.filterByCondition(returnItemList, filterCondition);
        
        if(returnItemList.size() > 0) {
            
            has_order_return = true;
            
        }
        
        
        return has_order_return;
        
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static boolean hasOrderMultipleReturns(List<GenericValue> returnItemList) {
         
         boolean multiple_returns = false;
         
         if(returnItemList == null) {
             Debug.logWarning("No return item list provided. Returning false", module);
             return false;
         }
         
        EntityCondition filterCondition = EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED");
        
       
        returnItemList = EntityUtil.filterByCondition(returnItemList, filterCondition);
        
        if(returnItemList.size() > 0) {
        
            String currentOrderId = null;
            String oldOrderId = null;

            GenericValue firstItem = EntityUtil.getFirst(returnItemList);

            currentOrderId = (String) firstItem.get("orderId");
            oldOrderId = currentOrderId;

             for(GenericValue returnItem : returnItemList) {

                currentOrderId  = (String) returnItem.get("orderId");

                if(!currentOrderId.equals(oldOrderId)) {
                    multiple_returns = true;
                    break;
                }

                oldOrderId = currentOrderId;

             }
             
        }
         
         return multiple_returns;
         
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static List<String> getOrderReturnIdList(List<GenericValue> returnItemList) {
         
         List<String> orderReturnIdList = new ArrayList<>();
         
         Map<String, Object> returnIdMap = new HashMap<>();
         
         EntityCondition filterCondition = EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED");
        
        returnItemList = EntityUtil.filterByCondition(returnItemList, filterCondition);
         
         if(returnItemList.size() > 0) {
             
             for(GenericValue returnItem : returnItemList) {
                 
                 String orderId = (String) returnItem.get("returnId");
                 
                 returnIdMap.put(orderId, null);
                 
             }
             
         }
         
         Set<String> keySet = returnIdMap.keySet();
         
         Iterator<String> keyset_iter = keySet.iterator();
         
         while(keyset_iter.hasNext()) {
             
             orderReturnIdList.add(keyset_iter.next());
             
         }
         
         return orderReturnIdList;
         
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static String getReturnItemsRefundTypeList(List<GenericValue> returnItemList) {
         
        ArrayList<String> refundTypeList = new ArrayList<>();
        StringBuilder refundTypeBuilder = new StringBuilder();
        
         
        if(returnItemList == null) {
            Debug.logWarning("Return item list is null. Return null", module);
            return null;
        }
        
        for(GenericValue returnItem : returnItemList) {
            
            String refundType = (String) returnItem.get("returnTypeId");
            
            if(!refundTypeList.contains(refundType)) {
                refundTypeList.add(refundType);
            }
            
        }
        
        if(refundTypeList.isEmpty()) {
            refundTypeBuilder.append("-");
        }else{
            for(String rfty : refundTypeList) {
                refundTypeBuilder.append(rfty).append(" ");
            }
        }
        
        return refundTypeBuilder.toString();
         
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static String getReturnItemsReturnMethodTypeList(List<GenericValue> returnItemList) {
         
        ArrayList<String> returnMethodTypeList = new ArrayList<>();
        StringBuilder returnMethodBuilder = new StringBuilder();
        
         
        if(returnItemList == null) {
            Debug.logWarning("Return item list is null. Return null", module);
            return null;
        }
        
        for(GenericValue returnItem : returnItemList) {
            
            String returnMethod = (String) returnItem.get("mpReturnMethod");
            
            if(!returnMethodTypeList.contains(returnMethod)) {
                returnMethodTypeList.add(returnMethod);
            }
            
        }
        
        if(returnMethodTypeList.isEmpty()) {
            returnMethodBuilder.append("-");
        }else{
            for(String rtnm : returnMethodTypeList) {
                returnMethodBuilder.append(rtnm).append(" ");
            }
        }
        
        return returnMethodBuilder.toString();
         
     }
     
     /**
      * 
      * @param returnItemList
      * @return 
      */
     protected static BigDecimal getRetunItemReturnableTotal(List<GenericValue> returnItemList) {
         
        BigDecimal refundTotal = BigDecimal.ZERO;
         
        if(returnItemList == null) {
            Debug.logWarning("Return item list is null: cannot calc refund total.", module);
            return BigDecimal.ZERO;
        }
         
        EntityCondition filterCondition = EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED");
        
        returnItemList = EntityUtil.filterByCondition(returnItemList, filterCondition);
        
        for(GenericValue returnItem : returnItemList) {
            
            BigDecimal returnit_qty = (BigDecimal) returnItem.get("returnQuantity");
            BigDecimal return_price = (BigDecimal) returnItem.get("returnPrice");
            
            
            refundTotal = refundTotal.add(return_price.multiply(returnit_qty));
            
        }
        
        return refundTotal;
         
         
     }
    
    /**
     * Get facility list for order item issuances.
     * @param itemIssuances
     * @param delegator
     * @return 
     */
    protected static List<GenericValue> getItemIssuanceFacilities(List<GenericValue> itemIssuances, Delegator delegator) {
         
        List<GenericValue> issuanceFacilityList = null;
        
        if(UtilValidate.isEmpty(itemIssuances)) {
            return null;
        }
        
        issuanceFacilityList = new ArrayList<>();
        
        for(GenericValue itemIssuance : itemIssuances) {
            
            String _issuanceShipmentId = (String) itemIssuance.get("shipmentId");
            
            //if not empty use shipment to get facility
            if(!_issuanceShipmentId.isEmpty()) {
                
                GenericValue _shipment = null;
                
                try {
                    _shipment = delegator.findOne("Shipment", UtilMisc.toMap("shipmentId", _issuanceShipmentId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee, "Error in retrieving Shipment record with id ["+_issuanceShipmentId+"]. Return.", module);
                    return null;
                }
                
                String _shipmentFacilityId = (String) _shipment.get("originFacilityId");
                
                GenericValue _shipmentFacility = null;
                
                try {
                    _shipmentFacility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", _shipmentFacilityId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee, "Error in retrieving Facility record with id ["+_shipmentFacilityId+"]. Return.", module);
                    return null;
                }
                
                issuanceFacilityList.add(_shipmentFacility);
                
            }else{ //use inventory item issued to retrieve facility
                
                String _inventoryItemId = (String) itemIssuance.get("inventoryItemId");
                
                GenericValue _invItem = null;
                
                try {
                    _invItem = delegator.findOne("InventoryItem", UtilMisc.toMap("inventoryItemId", _inventoryItemId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee, "Error in retrieving InventoryItem record with id ["+_invItem+"]. Return.", module);
                    return null;
                }
                
                String _invFacilityId = (String) _invItem.get("facilityId");
                
                GenericValue _inventoryFacility = null;
                
                try {
                    _inventoryFacility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", _invFacilityId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee, "Error in retrieving Facility record with id ["+_invFacilityId+"]. Return.", module);
                    return null;
                }
                
                issuanceFacilityList.add(_inventoryFacility);
                
            }
            
            
        }
        
        
        return issuanceFacilityList;
         
    } 
    
    /**
     * 
     * @param itemIssuances
     * @param delegator
     * @return 
     */
    protected static Map<String, String> getItemIssuanceFacilityMap(List<GenericValue> itemIssuances, Delegator delegator) {
        
        LinkedHashMap<String, String> itemIssuanceFacilityMap = null;
        
        if(UtilValidate.isEmpty(itemIssuances)) {
            return null;
        }
        
        itemIssuanceFacilityMap = new LinkedHashMap<>();
        
        for(GenericValue itemIssuance : itemIssuances) {
            
            String _issuanceShipmentId = (String) itemIssuance.get("shipmentId");
            
            String _itemIssuanceId = (String) itemIssuance.get("itemIssuanceId");
            
            //if not empty use shipment to get facility
            if(!_issuanceShipmentId.isEmpty()) {
                
                GenericValue _shipment = null;
                
                try {
                    _shipment = delegator.findOne("Shipment", UtilMisc.toMap("shipmentId", _issuanceShipmentId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee, "Error in retrieving Shipment record with id ["+_issuanceShipmentId+"]. Return.", module);
                    return null;
                }
                
                String _shipmentFacilityId = (String) _shipment.get("originFacilityId");
                
                itemIssuanceFacilityMap.put(_itemIssuanceId, _shipmentFacilityId);
                
            }else{ //use inventory item issued to retrieve facility
                
                String _inventoryItemId = (String) itemIssuance.get("inventoryItemId");
                
                GenericValue _invItem = null;
                
                try {
                    _invItem = delegator.findOne("InventoryItem", UtilMisc.toMap("inventoryItemId", _inventoryItemId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee, "Error in retrieving InventoryItem record with id ["+_invItem+"]. Return.", module);
                    return null;
                }
                
                String _invFacilityId = (String) _invItem.get("facilityId");
                
                itemIssuanceFacilityMap.put(_itemIssuanceId, _invFacilityId);
                
            }
            
        }
        
        
        return itemIssuanceFacilityMap;
        
    }
     
     /**
      * 
      * @param originNum
      * @param charToFind
      * @param charToReplace
      * @return 
      */
    protected static String replaceDecimalCharatcer(BigDecimal originNum, String charToFind, String charToReplace) {
         
        String newNumStr = "";
        
        if(originNum == null || charToFind == null || charToReplace == null) {
            return newNumStr;
        }
        
        String originNumStr = originNum.toPlainString();
        
        if(originNumStr.contains(charToFind)) {
            if(charToFind.equalsIgnoreCase(".")) {
                //escape it since the replace method uses regex
                charToFind = "\\"+charToFind;
            }
            
            newNumStr = originNumStr.replaceAll(charToFind, charToReplace);
        }else{
            newNumStr = originNumStr;
        }
        
        return newNumStr;
         
    }
    
    /**
     * 
     * @param facilityId
     * @param delegator
     * @return 
     */
    protected static String getFacilityName(String facilityId, Delegator delegator) {
        
        String facilityName = "";
        
        if(facilityId == null) {
            return null;
        }
        
        GenericValue _facility = null;
                
        try {
            _facility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", facilityId), false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee, "Error in retrieving Facility record with id ["+facilityId+"]. Return.", module);
            return null;
        }
        
        facilityName = (String) _facility.get("facilityName");
        
        return facilityName;
        
    }
    
    /**
     * 
     * @param issuanceFacilityMap <itemIssuanceId, facilityId>
     * @param delegator
     * @return 
     */
    protected static String getFormatShipmentFacilitiesAndDates(Map<String, String> issuanceFacilityMap, Delegator delegator) {
        
        StringBuilder formattedFacAndDates = new StringBuilder();
        
        if(issuanceFacilityMap == null ) {
            formattedFacAndDates.append("-");
            return formattedFacAndDates.toString();
        }
        
        
        for(Entry<String, String> entry : issuanceFacilityMap.entrySet()) {
            
            formattedFacAndDates.append("[ ");
            
            String _issuanceId = entry.getKey();
            String _facilityId = entry.getValue();
            
            String _facilityName = getFacilityName(_facilityId, delegator);
            
            //if the - exists in the name, then the chars before it are a simple code
            if(_facilityName.contains("-")) {
                String []nameParts = _facilityName.split("-");
                String _code = nameParts[0];
                formattedFacAndDates.append(_code);
            }else{
                formattedFacAndDates.append(_facilityName);
            }
            
            //Get the date from issuance
            GenericValue _itemIssuance = null;
            
            try {
                _itemIssuance = delegator.findOne("ItemIssuance", UtilMisc.toMap("itemIssuanceId", _issuanceId), false);
            }catch(GenericEntityException gee) {
                Debug.logError(gee, "Error in retrieving ItemIssuance record with id ["+_issuanceId+"]. Return.", module);
                return null;
            }
            
            Timestamp issuedDateTime = _itemIssuance.getTimestamp("issuedDateTime");
            
            String issuedDateTimeStr = getStringDateTimeFromTimestampFormat2(issuedDateTime);
            
            formattedFacAndDates.append(" ( ").append(issuedDateTimeStr).append(" )").append(" ]").append(" ");
            
        }
        
        return formattedFacAndDates.toString();
    }
    
    /**
     * 
     * @param orderItemShipGroupAssoc
     * @param delegator
     * @return 
     */
    protected static String getOrderItemFacilityReservation(List<GenericValue> orderItemShipGroupAssoc, Delegator delegator) {
        
        StringBuilder orderItemFacilityRes = new StringBuilder();
        
        if(orderItemShipGroupAssoc == null || UtilValidate.isEmpty(orderItemShipGroupAssoc)) {
            orderItemFacilityRes.append("-");
            return orderItemFacilityRes.toString();
        }
        
        
        for(GenericValue shipGroupAssoc : orderItemShipGroupAssoc) {
                
            String _shipGroupSeqId = (String) shipGroupAssoc.get("shipGroupSeqId");
            String _orderId = (String) shipGroupAssoc.get("orderId");
            String _orderItemSeqId = (String) shipGroupAssoc.get("orderItemSeqId");

            //Get Inventory reservation facility
            EntityCondition shpGrpInvResCond = EntityCondition.makeCondition(EntityOperator.AND,
                                            EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, _orderId),
                                            EntityCondition.makeCondition("orderItemSeqId", EntityOperator.EQUALS, _orderItemSeqId),
                                            EntityCondition.makeCondition("shipGroupSeqId", EntityOperator.EQUALS, _shipGroupSeqId));

            List<GenericValue> ordItmShipGrpInvRes = null;

            try {
                ordItmShipGrpInvRes = delegator.findList("OrderItemShipGrpInvRes", shpGrpInvResCond, null, null, null, false);
            }catch(GenericEntityException gee) {
                String msg = "Error in retrieving inventory reservation for order item ["+_orderId+"-"+_orderItemSeqId+"]. Cannot proceed.";
                Debug.logError(gee, msg, module);
                orderItemFacilityRes.append("-");
                return orderItemFacilityRes.toString();
            }
            
            orderItemFacilityRes.append("[ ");
            
            for(GenericValue oiShipGrpInvRes : ordItmShipGrpInvRes) {
                    
                String _inventoryItemId = (String) oiShipGrpInvRes.get("inventoryItemId");

                GenericValue inventoryItem = null;

                try {
                    inventoryItem = delegator.findOne("InventoryItem", UtilMisc.toMap("inventoryItemId", _inventoryItemId), false);
                }catch(GenericEntityException gee) {
                    String msg = "Error in retrieving inventory item for order item ["+_orderId+"-"+_orderItemSeqId+"].";
                    Debug.logError(gee.getMessage(), msg, module);
                }
                
                if(inventoryItem != null) {

                    String invFacilityId = (String) inventoryItem.get("facilityId");

                    String _facilityName = getFacilityName(invFacilityId, delegator);

                    //if the - exists in the name, then the chars before it are a simple code
                    if(_facilityName.contains("-")) {
                        String []nameParts = _facilityName.split("-");
                        String _code = nameParts[0];
                        orderItemFacilityRes.append("( ").append(_code).append(" ),");
                    }else{
                        orderItemFacilityRes.append("( ").append(_facilityName).append(" ),");
                    }
                    
                }

            }
            
            orderItemFacilityRes.append(" ]");
            
        }
        
        return orderItemFacilityRes.toString();
        
    }
    
    protected static boolean isPromotionAdjustment(List<GenericValue> orderAdjustmentList, String orderAdjustmentTypeId, String freeShippingPromos)
    {
        boolean isPromotionAdjustment = false;
        
        String[] freshipPromo = null;
        
        if(orderAdjustmentList == null) 
        {
            Debug.logWarning("Order Adjustment List is null: cannot get order promotion adjustment.", module);
            return isPromotionAdjustment;
        }
        
        if (freeShippingPromos.contains(","))
        {
        	freshipPromo = freeShippingPromos.split(",");
        }
        
        EntityCondition filterCondition = EntityCondition.makeCondition("orderAdjustmentTypeId", EntityOperator.EQUALS, orderAdjustmentTypeId);
         
        orderAdjustmentList = EntityUtil.filterByCondition(orderAdjustmentList, filterCondition);
         
        for(GenericValue orderAdjustment : orderAdjustmentList) 
        {
            String productPromoId = orderAdjustment.getString("productPromoId");
            
            for (int i = 0; i < freshipPromo.length; i++)
            {
                String promoFreeShip = freshipPromo[i].trim();
                
                if(productPromoId != null && productPromoId.equals(promoFreeShip))
                {
                    isPromotionAdjustment = true;
                }
            }
             
        }
            
        return isPromotionAdjustment;
    }
    
    
    protected static Map<String,String> getCustomerInformation(String orderId, Delegator delegator) 
    {
        Map<String, String> customerMap = new HashMap<>();

        OrderReadHelper orderHelper = new OrderReadHelper(delegator, orderId);

        GenericValue partyFromRole = orderHelper.getEndUserParty();

            // get Customer PartyId
            String customerPartyId = partyFromRole.getString("partyId");
            
            // get Customer registration system
            Timestamp createdDate = partyFromRole.getTimestamp("createdStamp");
            //Date date = new Date(ts.getTime());
            //String customerRegistrationDate = UtilDateTime.toDateString(date);
            String customerRegistrationDate = getStringDateFromTimestamp(createdDate);

            // get Customer First and Last Name
            String customerFirstName = null;
            String customerLastName = null;

            // get Customer City,CountryGeoId (Nazione), StateProvinceGeoId (Provincia) 
            String customerCity = null;
            String customerNation = null;
            String customerProvince = "";

            
            customerFirstName = partyFromRole.getString("firstName");
            customerLastName = partyFromRole.getString("lastName");
            
            //System.out.println("customerFirstName: "+customerFirstName);
            //System.out.println("customerLastName: "+customerLastName);
            

            GenericValue customerShippingAddress = orderHelper.getShippingAddress("00001");

            if (!customerShippingAddress.isEmpty()) 
            {
                customerCity = customerShippingAddress.getString("city");
                customerNation = customerShippingAddress.getString("countryGeoId");

                // because province is in this form: IT-XX, then split to return XX
                String customerStateProvince = customerShippingAddress.getString("stateProvinceGeoId");
                
                if(customerStateProvince == null || "_NA_".equals(customerStateProvince))
                {
                    customerProvince = "N/A";
                }else if(customerStateProvince.contains("-"))
                {
                    customerProvince = customerStateProvince.substring(customerStateProvince.indexOf("-")+1);
                }else{
                    customerProvince = "N/A";
                }
            }

            // get Customer Email
            String customerEmail = orderHelper.getOrderEmailString();

            customerMap.put("firstName", customerFirstName);
            customerMap.put("lastName", customerLastName);
            customerMap.put("email", customerEmail);
            customerMap.put("registrationDate", customerRegistrationDate);
            customerMap.put("city", customerCity);
            customerMap.put("province", customerProvince);
            customerMap.put("nation", customerNation);

            

        
        
        return customerMap;
    }
    
    protected static String getOrderProductPromoCode(String orderId, Delegator delegator)
    {
        OrderReadHelper orh = new OrderReadHelper(delegator, orderId);
        
        Set<String> promocodeEntered = orh.getProductPromoCodesEntered();
                
        StringBuilder orderPromoCodes = new StringBuilder();
                
        if(!promocodeEntered.isEmpty()) 
        {
            orderPromoCodes.append("1").append("-");
            
            int count = 1;
            
            int promoCodeSetSize = promocodeEntered.size();
            
            for(String promocode : promocodeEntered)
            {
                if(count == promoCodeSetSize)
                {
                    orderPromoCodes.append(promocode);
                }
                
                if(count < promoCodeSetSize)
                {
                    orderPromoCodes.append(promocode).append(",");
                }
           
                count = count + 1;
            }
            
        }else {
            orderPromoCodes.append("0");
        }
                
        return orderPromoCodes.toString();
        
    }
                                
    
    
}//end class
