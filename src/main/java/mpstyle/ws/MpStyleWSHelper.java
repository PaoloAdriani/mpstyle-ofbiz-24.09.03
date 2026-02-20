/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.ws;

import mpstyle.ws.availability.InventoryItems;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transaction;
import javax.transaction.Status;


/**
 *
 * @author equake58
 */
public class MpStyleWSHelper {
    
    public static final String MODULE = MpStyleWSHelper.class.getName();
    
    private static final String OMNI_SYSTEM_RESOURCE_ID = "mpomni";

    
    /**
     * Updates inventory levels (if necessary) with quantity retrieved via
     * ws.Returns a nested map, with cart item sequence id used as map key
     * @param iiObj
     * @param otherStoreFacilities
     * @param delegator
     * @param dispatcher
     * @return 
     */
    public static Map<String, Object> updateInventoryItemsAvailability(InventoryItems iiObj, List<String> otherStoreFacilities, Delegator delegator, LocalDispatcher dispatcher) {
        
       Map<String, Object>  cartItemProductAvailabilityMap = null;
       
        //user name and password for services
        String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "serviceUsername", delegator);
        String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "servicePassword", delegator);
       
       if(iiObj == null) {
           Debug.logError("InventoryItems response object is null. Return empty map.", MODULE);
           return new HashMap<>();
       }
       
       cartItemProductAvailabilityMap = new HashMap<>();
       
       //Get the list of InventoryItem objects
       List<InventoryItems.InventoryItem> ii_itemsList = iiObj.getInventoryItem();
       
       //The facilities here are the ones enabled for ws
       for(InventoryItems.InventoryItem ii_item : ii_itemsList) {
           
           String sku = ii_item.getSku();
           String orderId = ii_item.getOrderId();
           String orderItemSeqId = ii_item.getOrderItemSeqId();
           Map<String, Object> cartItemSkuAvail = new HashMap<>();
           cartItemSkuAvail.put("sku", sku);
           cartItemSkuAvail.put("instock", false);
           
           Debug.logWarning("Processing sku => "+sku, MODULE);
           
           List<InventoryItems.InventoryItem.Facilities.Facility> itemFaciltiyList = ii_item.getFacilities().getFacility();
           
           //Total ATP of a sku across all the facilities
           BigDecimal skuAtpTotal = BigDecimal.ZERO;
           BigDecimal skuQohTotal = BigDecimal.ZERO;
           
           for(InventoryItems.InventoryItem.Facilities.Facility fac : itemFaciltiyList) {
               String facilityId = fac.getId();
               BigDecimal facilityATP = fac.getAtp();
               
               Debug.logWarning("Facility ["+facilityId+"] => ATP ["+facilityATP+"]", MODULE);
               
               Map<String, Object> updateInvERPContextMap = new HashMap<>();
               
               updateInvERPContextMap.put("productId", sku);
               updateInvERPContextMap.put("facilityId", facilityId);
               updateInvERPContextMap.put("inputATP", facilityATP);
               updateInvERPContextMap.put("login.username", username);
               updateInvERPContextMap.put("login.password", password);
               
               Map<String, Object> updInvERPResultMap = null;
               
               try {
                   updInvERPResultMap = dispatcher.runSync("updateInventoryERPAvailability", updateInvERPContextMap);
               }catch(GenericServiceException gse) {
                   Debug.logError(gse.getMessage(), MODULE);
                   //Some errors like user auth failed or other: set the item as in stock and keep going.
                   cartItemSkuAvail.put("instock", true);
                   cartItemProductAvailabilityMap.put(orderItemSeqId, cartItemSkuAvail);
                   continue;
               }
               
               if(updInvERPResultMap != null && ServiceUtil.isError(updInvERPResultMap)) {
                   List<String> errorMessages = (List<String>) updInvERPResultMap.get("errorMessageList");
                   
               }
               
               if(updInvERPResultMap != null && ServiceUtil.isSuccess(updInvERPResultMap)) {
               
                    BigDecimal _skuAtp = (BigDecimal) updInvERPResultMap.get("availableToPromiseTotal");
                    BigDecimal _skuQoh = (BigDecimal) updInvERPResultMap.get("quantityOnHandTotal");
                    
                    skuAtpTotal = skuAtpTotal.add(_skuAtp);
                    skuQohTotal = skuQohTotal.add(_skuQoh);
                
               }
               
           }
           
            //Calc stock of other non-ws facilities (if any) and add it to the total
            if(otherStoreFacilities != null && !otherStoreFacilities.isEmpty()) {
                    
                Debug.logWarning("Processing other non-ws facilities", MODULE);
               
                for(String othFacilityId : otherStoreFacilities) {

                    Map<String, Object> invAvailByFac = new HashMap<>();
                    BigDecimal invProductFacATP = BigDecimal.ZERO;
                    BigDecimal invProductFacQOH = BigDecimal.ZERO;

                    boolean beginTransaction = false;

                    /*@giulio NOTE: service getInventoryAvailableByFacility require to be executed within a transaction
                    * so if one is not already in place, start a new one.
                    */
                    try {
                        
                        if (TransactionUtil.getStatus() == Status.STATUS_NO_TRANSACTION) {
                            beginTransaction = TransactionUtil.begin();
                        }
                            
                        try {

                            invAvailByFac.put("productId", sku);
                            invAvailByFac.put("facilityId", othFacilityId);
                            invAvailByFac.put("login.username", username);
                            invAvailByFac.put("login.password", password);

                            Map<String, Object> invAvailFacResultMap = dispatcher.runSync("getInventoryAvailableByFacility", invAvailByFac);

                            if(invAvailFacResultMap != null && ServiceUtil.isSuccess(invAvailFacResultMap)) {

                                invProductFacATP = (BigDecimal) invAvailFacResultMap.get("availableToPromiseTotal");
                                invProductFacQOH = (BigDecimal) invAvailFacResultMap.get("quantityOnHandTotal");
                                skuAtpTotal = skuAtpTotal.add(invProductFacATP);
                                skuQohTotal = skuQohTotal.add(invProductFacQOH);
                                

                            }
                            
                            Debug.logWarning("Facility ["+othFacilityId+"] => ATP ["+invProductFacATP+"]", MODULE);

                        }catch(GenericServiceException gse) {
                            String msg = "Error in running service getInventoryAvailableByFacility for product [" + sku + "] and facility [" + othFacilityId + "]. Error is => " + gse.getMessage();
                            Debug.logError(msg, MODULE);
                        }
                            
                    } catch (GenericTransactionException ex) {
                        try {
                            TransactionUtil.rollback(beginTransaction, "Error occured during transaction. Rolling back.", ex);
                        } catch (GenericTransactionException ex1) {
                            Debug.logError("Error in rolling back transaction. Error is => " + ex1.getMessage(), MODULE);
                        }
                    }finally {
                        try {
                            TransactionUtil.commit(beginTransaction);
                        } catch (GenericTransactionException ex) {
                            Debug.logError("Error in committing transaction. Error is => " + ex.getMessage(), MODULE);
                        }
                    }

                }
               
            }
           
           //if total sku atp (all facilities) is .gt. 0 then flag the product as "in stock (true)"
           if(skuAtpTotal.compareTo(BigDecimal.ZERO) == 1) {
               cartItemSkuAvail.put("instock", true);
           }
           
           cartItemProductAvailabilityMap.put(orderItemSeqId, cartItemSkuAvail);
           
       }
       
       return cartItemProductAvailabilityMap;
        
    }
    
}//end class
