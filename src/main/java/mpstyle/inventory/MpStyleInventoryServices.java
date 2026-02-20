/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.inventory;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 * @author equake58
 */
public class MpStyleInventoryServices {
    
    public static final String MODULE = MpStyleInventoryServices.class.getName();
    
    private static final String OMNI_SYSTEM_RESOURCE_ID = "mpomni";
    private final static String NON_SERIAL_INV_ITEM_TYPE = "NON_SERIAL_INV_ITEM";
    
    /**
     * 
     * @param dctx
     * @param context
     * @return 
     */
    public static Map<String, Object> updateInventoryERPAvailability(DispatchContext dctx, Map<String, Object> context) {
        
         
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        
        String productId = (String) context.get("productId");
        String facilityId = (String) context.get("facilityId");
        BigDecimal inputATP = (BigDecimal) context.get("inputATP");
        
        List<String> returnMessageList = new ArrayList<>();
        boolean error = false;
        Map<String, Object> returnMap = null;
        
        
        //user name and password for services
        String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "serviceUsername", delegator);
        String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "servicePassword", delegator);
        
        
        //Get AVAILABLE InventoryItem objects for the product in the specific facility
        EntityCondition cond = EntityCondition.makeCondition(EntityOperator.AND,
                                        EntityCondition.makeCondition("productId", productId),
                                            EntityCondition.makeCondition("facilityId",facilityId),
                                                EntityCondition.makeCondition("statusId","INV_AVAILABLE"));
            
        List<GenericValue> inventoryItemList = null;
        
        try {
            inventoryItemList = delegator.findList("InventoryItem", cond, null, UtilMisc.toList("lastUpdatedStamp DESC"), null, false);
        }catch(GenericEntityException gee) {
            String msg = "Error in retrieving InventoryItems for product ["+productId+"], in facility ["+facilityId+"]. Error is => "+gee.getMessage();
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnError(msg);
        }
        
        /* Total ATP/QOH calculated for a list of Inventory Items of a product in a specific facility.
        * They are updated by availability coming in from ERP ws.
        */
        BigDecimal invItemATP = BigDecimal.ZERO;
        BigDecimal invItemQOH = BigDecimal.ZERO;
        
        
        if(UtilValidate.isNotEmpty(inventoryItemList)) {
            
            /* get the last updated inventory item record as update target and update only that one,
                to avoid wrong atp calculations.
                Should we set other InventoryItems as DEFECTIVE, to avoid availability problems????
            */
            GenericValue lastUpdatedInventoryItem = EntityUtil.getFirst(inventoryItemList);
            
                String processedInventoryItemId = (String) lastUpdatedInventoryItem.get("inventoryItemId");
            
                Map<String, Object> invAvailContextMap = new HashMap<>();

                invAvailContextMap.put("inventoryItemId", processedInventoryItemId);

                Map<String, Object> availResMap = null;

                try {
                    availResMap = dispatcher.runSync("getInventoryAvailableByItem", invAvailContextMap);
                }catch(GenericServiceException gse) {
                    String msg = "Error in calling service getInventoryAvailableByItem. Error is => "+gse.getMessage();
                    Debug.logError(msg, MODULE);
                    return ServiceUtil.returnError(msg);
                }

                if(ServiceUtil.isError(availResMap)) {
                    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(availResMap));
                }

                BigDecimal quantityOnHandTotal = (BigDecimal) availResMap.get("quantityOnHandTotal");
                BigDecimal availableToPromiseTotal = (BigDecimal) availResMap.get("availableToPromiseTotal");

                Debug.logWarning("Found QOH TOTAL of "+quantityOnHandTotal+" for product "+productId+" in facility "+facilityId, MODULE);
                
                if(inputATP.compareTo(BigDecimal.ZERO) == -1) {
                    inputATP = BigDecimal.ZERO;
                }
						
                //Calc the difference between input ATP (from ws) and QOH value in the system
                BigDecimal availDiff = quantityOnHandTotal.subtract(inputATP);

                if(availDiff.compareTo(BigDecimal.ZERO) != 0) {
                    
                    Map<String, Object> physInvAndVarContextMap = new HashMap<>();
                    
                    physInvAndVarContextMap.put("inventoryItemId", processedInventoryItemId);
                    physInvAndVarContextMap.put("quantityOnHandVar", availDiff.negate());
                    physInvAndVarContextMap.put("availableToPromiseVar", availDiff.negate());
                    Timestamp physicalInventoryDateTs = new Timestamp(System.currentTimeMillis());
                    physInvAndVarContextMap.put("physicalInventoryDate", physicalInventoryDateTs);
                    physInvAndVarContextMap.put("login.username", username);
                    physInvAndVarContextMap.put("login.password", password);
                
                    Map<String, Object> resultPhysicalInventoryAndVariance = null;
                    
                    try {
                        resultPhysicalInventoryAndVariance = dispatcher.runSync("createPhysicalInventoryAndVariance", physInvAndVarContextMap);
                    }catch(GenericServiceException gse) {
                        String msg = "Error in execution of createPhysicalInventoryAndVariance. Error is =>"+gse.getMessage();
                        Debug.logError(msg, MODULE);
                        return ServiceUtil.returnError(msg);
                    }
                   
                    if(ServiceUtil.isError(resultPhysicalInventoryAndVariance)) {
                        error = true;
                        String msg = "|E| " + ServiceUtil.getErrorMessage(resultPhysicalInventoryAndVariance);
                        returnMessageList.add(msg);
                        Debug.logError(ServiceUtil.getErrorMessage(resultPhysicalInventoryAndVariance), MODULE);
                    }else{
                        //set a success message only if no error occured in previous iterations
                        String msg = "|I| Successfully updated productId ["+productId+"], in facility ["+facilityId+"]. Variance => "+ availDiff;
                        returnMessageList.add(msg);
                        
                        //recalculate availability and return it
                        invAvailContextMap = new HashMap<>();

                        invAvailContextMap.put("inventoryItemId", processedInventoryItemId);

                        availResMap = null;

                        try {
                            availResMap = dispatcher.runSync("getInventoryAvailableByItem", invAvailContextMap);
                        }catch(GenericServiceException gse) {
                            String msg2 = "Error in calling service getInventoryAvailableByItem. Error is => "+gse.getMessage();
                            Debug.logError(msg2, MODULE);
                            return ServiceUtil.returnError(msg2);
                        }
                        
                        if(ServiceUtil.isSuccess(availResMap)) {
                            invItemATP = (BigDecimal) availResMap.get("availableToPromiseTotal");
                            invItemQOH = (BigDecimal) availResMap.get("quantityOnHandTotal");
                        }

                    }
                   
                }else{
                    String msg = "|I| No update needed for product ["+productId+"] and inventoryItemId ["+processedInventoryItemId+"].";
                    returnMessageList.add(msg);
                    Debug.logInfo(msg, MODULE);
                    invItemATP = availableToPromiseTotal;
                    invItemQOH = quantityOnHandTotal;
                    
                }
                
           // } for
            
        }else{
            //no inventory item AVAILABLE found: create a new one
            
            String newInventoryItemId = null;
            
            if(inputATP.compareTo(BigDecimal.ZERO) == -1) {
                    inputATP = BigDecimal.ZERO;
            }
            
            String msg = "|I| No Inventory Items found for product ["+productId+"], in facility ["+facilityId+"] with status INV_AVAILABLE. Creating new one.";
            Debug.logWarning(msg, MODULE);
            
            Map<String, Object> createInvItemContextMap = new HashMap<>();
            createInvItemContextMap.put("facilityId", facilityId);
            createInvItemContextMap.put("inventoryItemTypeId", NON_SERIAL_INV_ITEM_TYPE);
            createInvItemContextMap.put("productId", productId);
            createInvItemContextMap.put("statusId", "INV_AVAILABLE");
            createInvItemContextMap.put("login.username", username);
            createInvItemContextMap.put("login.password", password);
            
            Map<String, Object> createInvItemResMap = null;

            try {
                createInvItemResMap = dispatcher.runSync("createInventoryItem", createInvItemContextMap);
            }catch(GenericServiceException gse) {
                String msg3 = "Error in calling service createInventoryItem. Error is => "+gse.getMessage();
                Debug.logError(msg3, MODULE);
                return ServiceUtil.returnError(msg3);
            }
            
            if(ServiceUtil.isSuccess(createInvItemResMap)) {
                newInventoryItemId = (String) createInvItemResMap.get("inventoryItemId");
                
                //Check if exists a record of ProductFacility entity for this productId and facilityId
                GenericValue productFacilityRecord = null;
                
                try {
                    productFacilityRecord = delegator.findOne("ProductFacility", UtilMisc.toMap("productId", productId, "facilityId", facilityId), true);
                }catch(GenericEntityException gee) {
                    String msg3b = "Error in retrieving ProductFacility record for product [" + productId + "] and facilityId ["+facilityId+"]. Error is => " + gee.getMessage();
                    Debug.logError(msg3b, MODULE);
                }
                
                //Create ProductFacility record
                if(productFacilityRecord == null) {
                
                    Map<String, Object> productFacilityContextMap = new HashMap<>();
                    productFacilityContextMap.put("facilityId", facilityId);
                    productFacilityContextMap.put("productId", productId);
                    productFacilityContextMap.put("login.username", username);
                    productFacilityContextMap.put("login.password", password);

                    Map<String, Object> productFacilityResMap = null;

                    try {
                        productFacilityResMap = dispatcher.runSync("createProductFacility", productFacilityContextMap);
                    }catch(GenericServiceException gse) {
                        //Keep the service going on with inventory update
                        String msg4 = "Error in calling service createProductFacility. Error is => "+gse.getMessage();
                        Debug.logError(msg4, MODULE);
                    }
                    
                    if(productFacilityResMap != null && !ServiceUtil.isSuccess(productFacilityResMap)) {
                        Debug.logError(ServiceUtil.getErrorMessage(productFacilityResMap), MODULE);
                    }
                
                }
                
                //create inventory variance
                Map<String, Object> createPhInvAndVarContextMap = new HashMap<>();
                createPhInvAndVarContextMap.put("inventoryItemId", newInventoryItemId);
                createPhInvAndVarContextMap.put("quantityOnHandVar", inputATP);
                createPhInvAndVarContextMap.put("availableToPromiseVar",inputATP);
                createPhInvAndVarContextMap.put("login.username", username);
                createPhInvAndVarContextMap.put("login.password", password);

                Map<String, Object> createPhInvAndVarResMap = null;

                try {
                    createPhInvAndVarResMap = dispatcher.runSync("createPhysicalInventoryAndVariance", createPhInvAndVarContextMap);
                }catch(GenericServiceException gse) {
                    String msg5 = "Error in calling service createPhysicalInventoryAndVariance. Error is => "+gse.getMessage();
                    Debug.logError(msg5, MODULE);
                    return ServiceUtil.returnError(msg5);
                }

                if(ServiceUtil.isSuccess(createPhInvAndVarResMap)) {
                    Debug.logWarning("Successfully created new inventory item ["+newInventoryItemId+"] for product ["+productId+"] with ATP/QOH of ["+inputATP+"].", MODULE);
                }

                //recalculate availability and return it
                Map<String, Object> invAvailContextMap = new HashMap<>();

                invAvailContextMap.put("inventoryItemId", newInventoryItemId);

                Map<String, Object> availResMap = null;

                try {
                    availResMap = dispatcher.runSync("getInventoryAvailableByItem", invAvailContextMap);
                }catch(GenericServiceException gse) {
                    String msg6 = "Error in calling service getInventoryAvailableByFacility. Error is => "+gse.getMessage();
                    Debug.logError(msg6, MODULE);
                    return ServiceUtil.returnError(msg6);
                }

                if(ServiceUtil.isSuccess(availResMap)) {
                    invItemATP = (BigDecimal) availResMap.get("availableToPromiseTotal");
                    invItemQOH = (BigDecimal) availResMap.get("quantityOnHandTotal");
                }
                    

            }
             
            
            
        }
        
       
        if(error) {
           returnMap = ServiceUtil.returnError(returnMessageList);
           returnMap.put("availableToPromiseTotal", invItemATP);
           returnMap.put("quantityOnHandTotal", invItemQOH);
           return returnMap;
        }
        
        returnMap = ServiceUtil.returnSuccess(returnMessageList);
        returnMap.put("availableToPromiseTotal", invItemATP);
        returnMap.put("quantityOnHandTotal", invItemQOH);
        
        return returnMap;
        
    }
    
} //end class
