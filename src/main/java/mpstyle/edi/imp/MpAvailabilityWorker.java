/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.edi.imp;

import mpstyle.edi.data.DataQOHErp;
import mpstyle.util.file.MpFileUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.product.product.ProductWorker;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;


/**
 *
 * @author equake58
 */
public class MpAvailabilityWorker {
    
    public static final String MODULE = MpAvailabilityWorker.class.getName();
    
    private static final String MPEDI_SYSTEM_RESOURCE_ID = "mpedi";
    private static final String BARCODE_EAN = "EAN";
    private static final String CSV_PIPE_SEP = "\\|";

    
    /**
     * 
     * @param filenamePath
     * @param historyPath
     * @param errorPath
     * @param barcodeType
     * @param username
     * @param password
     * @param delegator
     * @return 
     */
    public static Map<String, Object> importERPAvailabilityByBarcode(String filenamePath, String historyPath, String errorPath, String barcodeType, String username , String password, Delegator delegator, LocalDispatcher dispatcher) {
        
        Map<String, Object> resultMap = null;
        boolean processingError = false;
        List<String> processingErrorList = new ArrayList<>();
        List<String> skippedProductMsgList = null;
        int skippedProductCount = 0;
        int errorProductCount = 0;
        int totalProductCount = 0;
        
        /* Checks on input parameters */
        if(filenamePath == null || filenamePath.isEmpty()) {
            
            String msg = "File name path is null or empty. Quit processing.";
            processingError = true;
            processingErrorList.add(msg);
            
            Debug.logError(msg, MODULE);
            
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filenamePath, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        if(historyPath == null || historyPath.isEmpty()) {
            
            String msg = "History directory path is null or empty. Quit processing.";
            processingError = true;
            processingErrorList.add(msg);
            
            Debug.logError(msg, MODULE);
            
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filenamePath, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        if(errorPath == null || errorPath.isEmpty()) {
            
            String msg = "Error directory path is null or empty. Quit processing.";
            processingError = true;
            processingErrorList.add(msg);
            
            Debug.logError(msg, MODULE);
            
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filenamePath, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        if(username == null || username.isEmpty() || password == null || password.isEmpty()) {
            
            String msg = "Username and/or password is null or empty. Quit processing.";
            processingError = true;
            processingErrorList.add(msg);
            
            Debug.logError(msg, MODULE);
           
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filenamePath, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        if(barcodeType == null || barcodeType.trim().isEmpty()) {
            Debug.logWarning("Barcode type input parameter is null or missing. Assuming type: EAN", MODULE);
            barcodeType = BARCODE_EAN;
        }
        /* End checks on input parameters */
        
        File file = new File(filenamePath);
        String filename = null;
        
        if(file.isFile() && file.exists()) {
            filename = file.getName();
        }
        
        /* Reading active seasons form SystemProperty (comma separated list) */
        String activeSeasons = EntityUtilProperties.getPropertyValue(MPEDI_SYSTEM_RESOURCE_ID, "ecomActiveSeasons", delegator);
        
        boolean allSeasons = false;
        String []seasonsArray = null;
        List<String> activeSeasonList = new ArrayList<>();

        if(activeSeasons == null || UtilValidate.isEmpty(activeSeasons.trim())) {
            allSeasons = true;
        }else{
            seasonsArray = activeSeasons.split("\\,");

            for(String arrValue : seasonsArray) {
                    activeSeasonList.add(arrValue.trim());

            }
        }
        
        /* Reading existing facilities */
        List<String> existingFacilityIds = new ArrayList<>();
        
        List<GenericValue> facilityList = null;
        
        try {
            facilityList = delegator.findList("Facility", null, null, null, null, false);
        }catch(GenericEntityException gee) {
            String msg = "Error in reading existing Facilities. Abort processing. Error is => " + gee.getMessage();
          
            processingError = true;
            processingErrorList.add(msg);
            Debug.logError(msg, MODULE);
            
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        if(facilityList == null || UtilValidate.isEmpty(facilityList)) {
            String msg = "No Facility found. Abort processing.";
            
            processingError = true;
            processingErrorList.add(msg);
            Debug.logError(msg, MODULE);
            
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        for(GenericValue facility : facilityList) {
            if(!existingFacilityIds.contains((String)facility.get("facilityId"))) {
                existingFacilityIds.add((String)facility.get("facilityId"));
            }
        }
        
        /* All is set up. Start reading records from csv file. */
        List<DataQOHErp> csvDataList =  MpFileUtil.readCvs(filenamePath, CSV_PIPE_SEP);
        
        if(csvDataList == null || csvDataList.isEmpty()) {
            String msg = "No records found in csv file [" + filename + "]. Abort processing.";
            processingError = true;
            processingErrorList.add(msg);
            Debug.logError(msg, MODULE);
            
            resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
            return resultMap;
        }
        
        /* ##### Start looping on csv lines ##### */
        
        for(DataQOHErp csvData : csvDataList) {
            
            totalProductCount++;
            skippedProductMsgList = new ArrayList<>();
            
            String barcodeValue = csvData.getArticle();
            String facilityId = csvData.getFacility();
            BigDecimal erpAvailability = csvData.getAvailability();
            
            Debug.logWarning("Barcode Value: " + barcodeValue, MODULE);
            Debug.logWarning("Facility Id: " + facilityId, MODULE);
            Debug.logWarning("Erp Avail: " + erpAvailability.toPlainString(), MODULE);
            Debug.logWarning("\n", MODULE);
            
            
            /* Get the product from barcode value */
            GenericValue product = MpAvailabilityHelper.getProductFromBarocde(barcodeValue, barcodeType, delegator);
            
            //Skip line if product is null
            if(product == null) {
                String msg = "[" + barcodeType + " - " + barcodeValue + "] - Product not found. Skipping line.";
                Debug.logWarning(msg, MODULE);
                skippedProductCount++;
                skippedProductMsgList.add(msg);
                continue;
            }
            
            String productId = product.getString("productId");
            
            if(!existingFacilityIds.contains(facilityId)) {
                String msg = "[" + barcodeType + " - " + barcodeValue + "] - Facility from csv ["+facilityId+"] does not exists in the system. Skipping line.";
                Debug.logWarning(msg, MODULE);
                skippedProductCount++;
                skippedProductMsgList.add(msg);
                continue;
            }
            
            List<GenericValue> inventoryItemList = null;
            try {
                /* Get Available InventoryItem with given facility and productId */
                EntityCondition iicond = EntityCondition.makeCondition(EntityOperator.AND,
                                        EntityCondition.makeCondition("productId", 	productId),
                                        EntityCondition.makeCondition("facilityId",facilityId),
                                        EntityCondition.makeCondition("statusId","INV_AVAILABLE"));

                inventoryItemList = delegator.findList("InventoryItem", iicond, null, UtilMisc.toList("lastUpdatedStamp"), null, false);
            
            }catch(GenericEntityException gee) {
                
                String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in retrieving InventoryItem for productId [" + productId + "] in facility [" + facilityId + "]. Error is => " + gee.getMessage();
                processingError = true;
                processingErrorList.add(msg);
                Debug.logError(msg, MODULE);
            
                resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);
            
                return resultMap;
            }

            GenericValue inventoryItem = null;
            if(inventoryItemList != null && UtilValidate.isNotEmpty(inventoryItemList)) {
                inventoryItem = EntityUtil.getFirst(inventoryItemList);
            }
            
            /* If InventoryItem exists, then update it. */
            if(inventoryItem != null) {
                
                String inventoryItemId = inventoryItem.getString("inventoryItemId");
                
                //1 - Calculate availability for the inventory item
                Map<String, Object> result = null;
                try {
                  
                    result = dispatcher.runSync("getInventoryAvailableByItem", UtilMisc.toMap("inventoryItemId", inventoryItemId));
                
                }catch(GenericServiceException gse) {
                    String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [getInventoryAvailableByItem] for product [" + productId + "] Error is => " + gse.getMessage() + ". Skipping line.";
                    processingError = true;
                    processingErrorList.add(msg);
                    Debug.logError(msg, MODULE);

                    resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                    return resultMap;
                }
                
                if(!ServiceUtil.isSuccess(result)) {
                    String errorMsg = (String) result.get("errorMessage");
                    errorMsg = errorMsg + "[" + productId + "]";
                    processingErrorList.add(errorMsg);
                    Debug.logError("Error in service [getInventoryAvailableByItem] for product ["+productId+"] Error is => "+ errorMsg +". Skip this line.", MODULE);
                    processingError = true;
                    errorProductCount++;
                    continue;
                }
                
                BigDecimal availability = (BigDecimal) result.get("quantityOnHandTotal"); //quantityOnHandTotal
                //int csvAvailability = Integer.parseInt(erpAvailabilityString);
                BigDecimal csvAvailability = erpAvailability;
                
                if(csvAvailability.compareTo(BigDecimal.ZERO) < 0  )
                {
                    csvAvailability = BigDecimal.ZERO;
                }
                
                BigDecimal diffbd = availability.subtract(csvAvailability);

                Timestamp ts = new Timestamp(System.currentTimeMillis());
                
                //2 - Create variance
                if(diffbd.compareTo(BigDecimal.ZERO) > 0) {
                    
                    Map<String, Object> _ctxMap = new HashMap<>();
                    Map<String, Object> result2 = null;
                    
                    _ctxMap.put("inventoryItemId", inventoryItemId);
                    _ctxMap.put("quantityOnHandVar", diffbd.negate());
                    _ctxMap.put("availableToPromiseVar", diffbd.negate());
                    _ctxMap.put("physicalInventoryDate", ts);
                    _ctxMap.put("login.username", username);
                    _ctxMap.put("login.password", password);
                    
                    try {
                        result2 = dispatcher.runSync("createPhysicalInventoryAndVariance", _ctxMap);
                    } catch (GenericServiceException ex) {
                        String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [createPhysicalInventoryAndVariance] for product [" + productId + "] Error is => " + ex.getMessage() + ". Skipping line.";
                        processingError = true;
                        processingErrorList.add(msg);
                        Debug.logError(msg, MODULE);

                        resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                        return resultMap;
                    }
                    
                    if(!ServiceUtil.isSuccess(result2)) {
                        String errorMsg = (String) result2.get("errorMessage");
                        errorMsg = errorMsg + "[" + productId + "]";
                        processingErrorList.add(errorMsg);
                        Debug.logError("Error in service [createPhysicalInventoryAndVariance] for product [" + productId + "] Error is => "+ errorMsg +". Skip this line.", MODULE);
                        processingError = true;
                        errorProductCount++;
                        continue;
                    }
                    
                    
                    
                }else{
                    //diffbd <= 0
                    /* il valore assoluto è minore di zero, significa che c'è un qualche problema e
                     * o i dati non sono allineati o sono stati venduti più capi di quelli presenti
                     * il risultato non può essere niente altro che un valore negativo
                    */
                    Map<String, Object> result2 = null;
                    Map<String, Object> _ctxMap = new HashMap<>();
                    
                    _ctxMap.put("inventoryItemId", inventoryItemId);
                    _ctxMap.put("quantityOnHandVar", diffbd.negate());
                    _ctxMap.put("availableToPromiseVar", diffbd.negate());
                    _ctxMap.put("physicalInventoryDate", ts);
                    _ctxMap.put("login.username", username);
                    _ctxMap.put("login.password", password);
                    
                    try {
                        result2 = dispatcher.runSync("createPhysicalInventoryAndVariance", _ctxMap);
                    }catch(GenericServiceException gse) {
                        
                        String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [createPhysicalInventoryAndVariance] for product [" + productId + "] Error is => " + gse.getMessage() + ". Aborting.";
                        processingError = true;
                        processingErrorList.add(msg);
                        Debug.logError(msg, MODULE);

                        resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                        return resultMap;
                            
                    }

                    if(!ServiceUtil.isSuccess(result2)) {
                        String errorMsg = (String) result2.get("errorMessage");
                        errorMsg = errorMsg + "[" + productId + "]";
                        processingErrorList.add(errorMsg);
                        Debug.logError("(diffbd < 0) Error in service [createPhysicalInventoryAndVariance] for product ["+ productId +"] Error is => "+ errorMsg +". Skip this line.", MODULE);
                        processingError = true;
                        errorProductCount++;
                        continue;
                    }
                }
                
            }else{
               //InventoryItem does not exists. Create a brand new one.
               BigDecimal csvAvailability = erpAvailability;
               
                if(csvAvailability.compareTo(BigDecimal.ZERO) < 0 ) {
                    csvAvailability = BigDecimal.ZERO;
                }
                
                //1 - Create new inventory item
                Map<String, Object> result3 = null;
                Map<String, Object> _ctxMap = new HashMap<>();
                
                _ctxMap.put("facilityId", facilityId);
                _ctxMap.put("inventoryItemTypeId", "NON_SERIAL_INV_ITEM");
                _ctxMap.put("productId", productId);
                _ctxMap.put("statusId", "INV_AVAILABLE");
                _ctxMap.put("login.username", username);
                _ctxMap.put("login.password", password);
                
                try {
                    result3 = dispatcher.runSync("createInventoryItem", _ctxMap);
                }catch(GenericServiceException gse) {
                    String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [createInventoryItem] for product [" + productId + "] Error is => " + gse.getMessage() + ". Aborting.";
                    processingError = true;
                    processingErrorList.add(msg);
                    Debug.logError(msg, MODULE);

                    resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                    return resultMap;
                }
                
                if(!ServiceUtil.isSuccess(result3)) {
                    String errorMsg = (String) result3.get("errorMessage");
                    errorMsg = errorMsg + "[" + productId + "]";
                    processingErrorList.add(errorMsg);
                    Debug.logError("Error in service [createInventoryItem] for product ["+ productId +"] Error is => "+ errorMsg +". Skip this line.", MODULE);
                    processingError = true;
                    errorProductCount++;
                    continue;
                }
                
                String generatedInventoryItemId = (String) result3.get("inventoryItemId");
                
                //2 - Get the parent product
//                EntityCondition fathercondition = EntityCondition.makeCondition(EntityOperator.AND,
//                                                  EntityCondition.makeCondition("productIdTo", EntityOperator.EQUALS, productId),
//                                                  EntityCondition.makeCondition("productAssocTypeId", EntityOperator.EQUALS, "PRODUCT_VARIANT"));
//
//                List<GenericValue> productAssocList = delegator.findList("ProductAssoc", fathercondition, UtilMisc.toSet("productId"), null, null, false);
//                
                GenericValue productFh = ProductWorker.getParentProduct(productId, delegator);
                String productFhId = null;
                
                if(productFh != null) {
                    productFhId = (String) productFh.get("productId");
                }
                
                //3 - Check if exists in parent product in ProductFacility entity
                EntityCondition prodfacilityvarcond = EntityCondition.makeCondition(EntityOperator.AND,
                                                    EntityCondition.makeCondition("productId", EntityOperator.EQUALS, productFhId),
                                                    EntityCondition.makeCondition("facilityId",EntityOperator.EQUALS,facilityId));

                List<GenericValue> productFacilityFatherList = null;
                
                try {
                    productFacilityFatherList = delegator.findList("ProductFacility", prodfacilityvarcond, null, null, null, false);
                }catch(GenericEntityException gee) {
                    String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service retrieving ProductFacility record for parent product [" + productFhId + "] Error is => " + gee.getMessage() + ". Aborting.";
                    processingError = true;
                    processingErrorList.add(msg);
                    Debug.logError(msg, MODULE);

                    resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                    return resultMap;
                }
                
                
                if(productFacilityFatherList == null && UtilValidate.isEmpty(productFacilityFatherList)) {
                    
                    //3.1 Create ProductFacility record for parent product
                    
                    Map<String, Object> result4 = null;
                    Map<String, Object> _ctxMap4 = new HashMap<>();
                    
                    _ctxMap4.put("facilityId", facilityId);
                    _ctxMap4.put("productId", productFhId);
                    _ctxMap4.put("login.username", username);
                    _ctxMap4.put("login.password", password);
                    
                    try {
                        result4 = dispatcher.runSync("createProductFacility", _ctxMap4);
                    }catch(GenericServiceException gse) {
                        String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [createProductFacility] for parent product [" + productFhId + "] and facility [" + facilityId + "] Error is => " + gse.getMessage() + ". Aborting.";
                        processingError = true;
                        processingErrorList.add(msg);
                        Debug.logError(msg, MODULE);

                        resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                        return resultMap;
                    }
                    
                    if(!ServiceUtil.isSuccess(result4)) {
                        String errorMsg = (String) result4.get("errorMessage");
                        errorMsg = errorMsg + "[" + productId + "]";
                        processingErrorList.add(errorMsg);
                        Debug.logError("Error in service [createProductFacility] for parent product ["+productFhId+"] and Facility [" + facilityId + "] Error is => "+ errorMsg +". Skip this line.", MODULE);
                        processingError = true;
                        errorProductCount++;
                        continue;
                    }
                    
                }
                
                //3.2 Check if exists ProductFacility record for variant product
                EntityCondition productFacilitycond = EntityCondition.makeCondition(EntityOperator.AND,
                                                    EntityCondition.makeCondition("productId", EntityOperator.EQUALS, productId),
                                                    EntityCondition.makeCondition("facilityId",EntityOperator.EQUALS, facilityId));

                List<GenericValue> varProductFacilityList = null;

                try {
                    varProductFacilityList = delegator.findList("ProductFacility", productFacilitycond, null, null, null, false);
                }catch(GenericEntityException gee) {
                    String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service retrieving ProductFacility record for variant product [" + productId + "] Error is => " + gee.getMessage() + ". Aborting.";
                    processingError = true;
                    processingErrorList.add(msg);
                    Debug.logError(msg, MODULE);

                    resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                    return resultMap;
                }
                
                if(varProductFacilityList == null && UtilValidate.isEmpty(varProductFacilityList)) {
                    
                    //3.3 Create ProductFacility record for variant product
                    Map<String, Object> result5 = null;
                    Map<String, Object> _ctxMap5 = new HashMap<>();
                    
                    _ctxMap5.put("facilityId", facilityId);
                    _ctxMap5.put("productId", productId);
                    _ctxMap5.put("login.username", username);
                    _ctxMap5.put("login.password", password);

                    try {
                        result5 = dispatcher.runSync("createProductFacility", _ctxMap);
                    }catch(GenericServiceException gse) {
                        String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [createProductFacility] for variant product [" + productId + "] and facility [" + facilityId + "] Error is => " + gse.getMessage() + ". Aborting.";
                        processingError = true;
                        processingErrorList.add(msg);
                        Debug.logError(msg, MODULE);

                        resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                        return resultMap;
                    }

                    if(!ServiceUtil.isSuccess(result5)) {
                        String errorMsg = (String) result5.get("errorMessage");
                        errorMsg = errorMsg + "[" + productId + "]";
                        processingErrorList.add(errorMsg);
                        Debug.logError("Error in service [createProductFacility] for variant product ["+ productId +"] and Facility [" + facilityId + "] Error is => "+ errorMsg +". Skip this line.", MODULE);
                        processingError = true;
                        errorProductCount++;
                        continue;
                    }

                }
                
                //4 - Create inventory variance 
                Map<String, Object> result6 = null;
                Map<String, Object> _ctxMap6 = new HashMap<>();
                
                _ctxMap6.put("inventoryItemId", generatedInventoryItemId);
                _ctxMap6.put("quantityOnHandVar", csvAvailability);
                _ctxMap6.put("availableToPromiseVar", csvAvailability);
                _ctxMap6.put("login.username", username);
                _ctxMap6.put("login.password", password);
                
                try {
                    result6 = dispatcher.runSync("createPhysicalInventoryAndVariance", _ctxMap6);
                }catch(GenericServiceException gse) {
                    String msg = "[" + barcodeType + " - " + barcodeValue + "]. Error in service [createPhysicalInventoryAndVariance] for new inventory item [" + generatedInventoryItemId + "] Error is => " + gse.getMessage() + ". Aborting.";
                    processingError = true;
                    processingErrorList.add(msg);
                    Debug.logError(msg, MODULE);

                    resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

                    return resultMap;
                }

                if(!ServiceUtil.isSuccess(result6)) {
                    String errorMsg = (String) result6.get("errorMessage");
                    errorMsg = errorMsg + "[" + productId + "]";
                    processingErrorList.add(errorMsg);
                    Debug.logError("Error in service [createPhysicalInventoryAndVariance] for inventory item ["+generatedInventoryItemId+"]. Error is => "+ errorMsg +". Skip this line.", MODULE);
                    processingError = true;
                    errorProductCount++;
                    continue;
                }

            } ///end if-else (inventoryItem != null)
            
            
            /* TODO
            Perform some queries to retrieve season from the product
            and verify if is part of an active season.
            
            ....
            productSeason = "....";
            if(!activeSeasonList.contains(productSeason)) {
                Debug.logError("Product ["+varProductId+"] of season ["+ productSeason  +"] is part of non active season. Skip it.", MODULE);
                skippedProductCount++;
                continue;
            }
            ....
            
            */
            
            
            
        } //end loop on csv lines
        
        if(processingError) {
        
            boolean status = MpFileUtil.moveToDirectory(filenamePath, errorPath);
            Debug.logWarning("File moved? " + status + ". File [" + filename + "] moved into error directory due to processing errors", MODULE);
        
        }else{
            //Success: move file into history
            boolean status = MpFileUtil.moveToDirectory(filenamePath, historyPath);
            Debug.logWarning("File moved? " + status + ". File [" + filename + "] moved into history directory", MODULE);

        }
        
        resultMap = MpAvailabilityHelper.buildReturnResultMap(filename, processingError, processingErrorList, skippedProductMsgList, skippedProductCount, errorProductCount, totalProductCount);

        return resultMap;
        
    } //end method

    /**
     *
     * @return
     */
    public static Map<String, Object> importAvailabilityCsvFile(String absoluteFilenamePath, String historyDirPath,
                                                                String username, String password, GenericValue userLogin, Delegator delegator, LocalDispatcher dispatcher) {

        Map<String, Object> returnMap = null;
        boolean moved = false;

        Debug.logInfo("=== Processing csv file " + absoluteFilenamePath + " ===", MODULE);

        //Reading lines from file
        List<DataQOHErp> dataQohErpList =  MpFileUtil.readCvs(absoluteFilenamePath, MpFileUtil.CSV_PIPE_SEPARATOR);

        if (dataQohErpList == null || dataQohErpList.isEmpty()) {
            String msg = "No QOH data found from csv file. Quit importing availabilities.";
            return ServiceUtil.returnError(msg);
        }

        //reading active seasons form SystemProperty (comma separated list)
        String activeSeasonsProp = EntityUtilProperties.getPropertyValue("mpedi", "ecomActiveSeasons", delegator);

        boolean allSeasons = false;
        ArrayList<String> activeSeasonList = null;
        ArrayList<String> existingFacilityIds = null;

        if(activeSeasonsProp == null || UtilValidate.isEmpty(activeSeasonsProp)) {
            allSeasons = true;
        } else {
            if (activeSeasonsProp.contains(MpFileUtil.CSV_COMMA_SEPARATOR)) {
                String []seasonsArray = activeSeasonsProp.split(MpFileUtil.CSV_COMMA_SEPARATOR);
                activeSeasonList = new ArrayList<>(Arrays.asList(seasonsArray));
            } else {
                activeSeasonList = new ArrayList<>();
                activeSeasonList.add(activeSeasonsProp);
            }
        }

        //reading all the existing facilities
        List<GenericValue> facilityList = null;
        try {
            facilityList = delegator.findList("Facility", null, null, null, null, true);
        } catch (GenericEntityException e) {
            String msg = "Error in retrieving active facilities. Abort inventory loading. Msg => " + e.getMessage();
            return ServiceUtil.returnError(msg);
        }

        if(facilityList == null || UtilValidate.isEmpty(facilityList)) {
            String msg = "No facilities found. Abort inventory loading.";
            return ServiceUtil.returnError(msg);
        }

        existingFacilityIds = facilityList.stream()
                .map(gv -> gv.getString("facilityId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        //Start loop on the QOH pojos
        for (DataQOHErp _dataQOh : dataQohErpList) {

            String _dataFacilityId = _dataQOh.getFacility();
            String _dataVariantId = _dataQOh.getArticle();
            BigDecimal _dataAvailability = _dataQOh.getAvailability();
            _dataAvailability = (_dataAvailability.compareTo(BigDecimal.ZERO) <= 0) ? BigDecimal.ZERO : _dataAvailability;

            Debug.logInfo("=== Processing QOH data => "+ _dataFacilityId + "-" + _dataVariantId + "-" + _dataAvailability + "===", MODULE);

            //check on the existence of the sku before all: if the product sku does not exist, then skip the record
            String productSeason = _dataVariantId.substring(0, 2);

            //skip product if is not of an active season
            if(!allSeasons && !activeSeasonList.contains(productSeason)) {
                Debug.logWarning("Product [" + _dataVariantId + "] of season ["+ productSeason  +"] is part of non active season. Skip it.", MODULE);
                continue;
            }

            GenericValue varProduct = null;
            try {
                varProduct = delegator.findOne("Product", UtilMisc.toMap("productId", _dataVariantId), true);
            } catch (GenericEntityException e) {
                String msg = "Error in retrieving variant product for ID " + _dataVariantId + ". Msg => " + e.getMessage();
                return ServiceUtil.returnError(msg);
            }

            if(varProduct == null) {
                Debug.logWarning("# Product [" + _dataVariantId + "] does not exists. Skipping record.", MODULE);
                continue;
            }

            if(!existingFacilityIds.contains(_dataFacilityId)) {
                Debug.logWarning("Facility from csv [" + _dataFacilityId + "] does not exists in the system. Skip this line.", MODULE);
                continue;
            }

            /* Check if there are any AVAILABLE InventoryItem for the current
             * variant for specific facilityId and variantId.
             * Order by the lastUpdatedStamp DESC (last updated first).
             */
            List<GenericValue> variantInventoryItemList = null;

            try {
                variantInventoryItemList = EntityQuery.use(delegator).from("InventoryItem")
                        .where("productId", _dataVariantId, "facilityId", _dataFacilityId, "inventoryItemTypeId", "NON_SERIAL_INV_ITEM", "statusId", "INV_AVAILABLE")
                        .orderBy("-lastUpdatedStamp")
                        .cache(true)
                        .queryList();

            } catch (GenericEntityException e) {
                String msg = "Error in retrieving AVAILABLE InventoryItems for product [" + _dataVariantId + "] and facility [" + _dataFacilityId + "]." +
                        " Aborting. Msg => " + e.getMessage();
                return ServiceUtil.returnError(msg);
            }

            String variantLastInvItemId = "";
            if (variantInventoryItemList != null && !variantInventoryItemList.isEmpty()) {
                variantLastInvItemId = (EntityUtil.getFirst(variantInventoryItemList)).getString("inventoryItemId");
            }

            //InventoryItem exists, so use it for the update
            if(!UtilValidate.isEmpty(variantLastInvItemId)) {

                Map<String, Object> srvResultMap = null;
                try {
                    srvResultMap = dispatcher.runSync("getInventoryAvailableByItem", UtilMisc.toMap("inventoryItemId", variantLastInvItemId));
                } catch (GenericServiceException e) {
                    String msg = "Error running service getInventoryAvailableByItem for variant ID [" +
                            _dataVariantId + "] and inventory item ID [" + variantLastInvItemId + "]. Aborting. Msg => " + e.getMessage();
                    return ServiceUtil.returnError(msg);
                }

                if(ServiceUtil.isSuccess(srvResultMap)) {
                    BigDecimal qohTotal = ((BigDecimal) srvResultMap.get("quantityOnHandTotal")); //quantityOnHandTotal
                    BigDecimal availDiff = qohTotal.subtract(_dataAvailability);
                    Timestamp ts = new Timestamp(System.currentTimeMillis());

                    //Create inventory variance on existing InventoryItem
                    if(availDiff.compareTo(BigDecimal.ZERO) > 0) {
                        srvResultMap.clear();
                        try {
                            srvResultMap = dispatcher.runSync("createPhysicalInventoryAndVariance",
                                    UtilMisc.toMap("inventoryItemId", variantLastInvItemId,
                                            "quantityOnHandVar", availDiff.negate(), "availableToPromiseVar", availDiff.negate(),
                                            "login.username", username, "login.password", password, "physicalInventoryDate", ts));
                        } catch (GenericServiceException e) {
                            String msg = "(availDiff > 0) Error in calling service [createPhysicalInventoryAndVariance] for inventoryItemId [" + variantLastInvItemId + "]. Abort. Msg => " + e.getMessage();
                            return ServiceUtil.returnError(msg);
                        }

                        if (!ServiceUtil.isSuccess(srvResultMap)) {
                            String msg = ServiceUtil.getErrorMessage(srvResultMap);
                            return ServiceUtil.returnError(msg);
                        }

                    } else {
                        /* il valore assoluto è minore di zero, significa che c'è un qualche problema e
                           o i dati non sono allineati o sono stati venduti più capi di quelli presenti
                           il risultato non può essere niente altro che un valore negativo
                         */
                        srvResultMap.clear();
                        try {
                            srvResultMap = dispatcher.runSync("createPhysicalInventoryAndVariance",
                                    UtilMisc.toMap("inventoryItemId", variantLastInvItemId,
                                            "quantityOnHandVar", availDiff.negate(), "availableToPromiseVar", availDiff.negate(),
                                            "login.username", username, "login.password", password, "physicalInventoryDate", ts));
                        } catch (GenericServiceException e) {
                            String msg = "(availDiff <= 0) Error in calling service [createPhysicalInventoryAndVariance] for inventoryItemId [" + variantLastInvItemId + "]. Abort. Msg => " + e.getMessage();
                            return ServiceUtil.returnError(msg);
                        }

                        if (!ServiceUtil.isSuccess(srvResultMap)) {
                            String msg = ServiceUtil.getErrorMessage(srvResultMap);
                            return ServiceUtil.returnError(msg);
                        }
                    }
                }

            } else {
                //No Inventory Item found: create it with
                Map<String, Object> srvResultMap = null;
                String _newInventoryItemId = "";

                try {
                    srvResultMap = dispatcher.runSync("createInventoryItem",
                            UtilMisc.toMap("facilityId", _dataFacilityId, "inventoryItemTypeId", "NON_SERIAL_INV_ITEM", "productId", _dataVariantId, "login.username", username, "login.password", password, "statusId", "INV_AVAILABLE"));
                } catch (GenericServiceException e) {
                    String msg = "Error in running service createInventoryItem for facilityId [" + _dataFacilityId + "], variantId [" + _dataVariantId + "]. Msg => " + e.getMessage();
                    return ServiceUtil.returnError(msg);
                }

                if (!ServiceUtil.isSuccess(srvResultMap)) {
                    String msg = ServiceUtil.getErrorMessage(srvResultMap);
                    return ServiceUtil.returnError(msg);
                }

                _newInventoryItemId = (String) srvResultMap.get("inventoryItemId");

                /* CHECK: verifico esistenza record ProductFacility.
                 * Non necessario record del prodotto padre in quanto virtuale.
                 * Creo record per prodotto variante.
                 */

                GenericValue parentProduct = ProductWorker.getParentProduct(_dataVariantId, delegator);
                String parentProductId = parentProduct.getString("productId");

                /* NON PIÙ NECESSARIO: verifico se presente il padre
                List<GenericValue> productFacilityFather = EntityQuery.use(delegator).from("ProductFacility")
                        .where("productId", parentProductId, "facilityId", _dataFacilityId)
                        .cache(true)
                        .queryList();

                if(productFacilityFather != null && productFacilityFather.size() > 0) {

                }else{

                    //create father

                    Debug.logInfo("############ CREATE PRODUCT FACILITY FATHER: "+productFhId, MODULE);

                    //CREATE OFBIZ INVENTORY VARIANCE

                    Map<String, Object> resultt = null;

                    resultt = runService("createProductFacility",
                            UtilMisc.toMap("facilityId",temp.getFacility(),
                                    "productId",productFhId,"login.username",
                                    username, "login.password",password));
                }
                 */


                //Check if ProductFacility record for the variant exists; if not, create it
                List<GenericValue> variantProductFacilityList = null;
                try {
                    variantProductFacilityList = EntityQuery.use(delegator).from("ProductFacility")
                            .where("productId", _dataVariantId, "facilityId", _dataFacilityId)
                            .cache(true)
                            .queryList();
                } catch (GenericEntityException e) {
                    String msg = "Error in retrieving ProductFacility record for facilityId [" + _dataFacilityId + "] and variantId [" + _dataVariantId + "]. Msg => " + e.getMessage();
                    return ServiceUtil.returnError(msg);
                }

                //Create ProductFacility record for the variant product
                if (variantProductFacilityList == null || variantProductFacilityList.isEmpty()) {
                    srvResultMap.clear();
                    try {
                        srvResultMap = dispatcher.runSync("createProductFacility",
                                UtilMisc.toMap("facilityId", _dataFacilityId, "productId", _dataVariantId, "login.username", username, "login.password", password));
                    } catch (GenericServiceException e) {
                        String msg = "Error running service createProductFacility for facilityId [" + _dataFacilityId + "] and variantId [" + _dataVariantId + "]. Msg => " + e.getMessage();
                        return ServiceUtil.returnError(msg);
                    }

                    if (!ServiceUtil.isSuccess(srvResultMap)) {
                        String msg = ServiceUtil.getErrorMessage(srvResultMap);
                        return ServiceUtil.returnError(msg);
                    }
                }

                //Create the inventory variance on the newly created InventoryItem
                srvResultMap.clear();
                try {
                    srvResultMap = dispatcher.runSync("createPhysicalInventoryAndVariance",
                            UtilMisc.toMap("inventoryItemId", _newInventoryItemId,
                                    "quantityOnHandVar", _dataAvailability, "availableToPromiseVar", _dataAvailability,
                                    "login.username", username, "login.password", password));
                } catch (GenericServiceException e) {
                    String msg = "Error in running service createPhysicalInventoryAndVariance for InventoryItemId [" + variantLastInvItemId + "], " +
                            "quantityOnHandVar [" + _dataAvailability + "], availableToPromiseVar [" + _dataAvailability + "]. Abort. Msg => " + e.getMessage();
                    return ServiceUtil.returnError(msg);
                }

                if (!ServiceUtil.isSuccess(srvResultMap)) {
                    String msg = ServiceUtil.getErrorMessage(srvResultMap);
                    return ServiceUtil.returnError(msg);
                }
            } //end branch: inventory item not existing
        } //end loop on DataQOHErp

        /* No more needed
        Map<String, Object> r = null;
        r = runService("checkLastInventoryCount", UtilMisc.toMap("enabledOnly",false));
        */

        //Move processed file to history dir
        moved = MpFileUtil.moveToDirectory (absoluteFilenamePath, historyDirPath);
        Debug.logInfo("=== Moved " + absoluteFilenamePath + "to " + historyDirPath + "? " + moved, MODULE);
        returnMap = ServiceUtil.returnSuccess("File " + absoluteFilenamePath + " imported correctly");
        return returnMap;

    } //end method

    /**
     *
     * @param absoluteFilenamePath
     * @param historyDirPath
     * @param txTimeout
     * @param username
     * @param password
     * @param nowDateStr
     * @param dispatcher
     * @return
     */
    public static Map<String, Object> importXMLFile(String absoluteFilenamePath, String historyDirPath,
                                                 String username, String password,  Integer txTimeout, String nowDateStr, LocalDispatcher dispatcher) {

        Map<String, Object> returnMap = null;
        boolean moved = false;

        Debug.logInfo("=== Importing file " + absoluteFilenamePath + " with service entityImport ===", MODULE);

        if (absoluteFilenamePath == null || absoluteFilenamePath.trim().isEmpty()) {
            String msg = "File name to import is null or empty. Cannot import.";
            return ServiceUtil.returnError(msg);
        }

        if (historyDirPath == null || historyDirPath.trim().isEmpty()) {
            String msg = "History directory path is null or empty. Cannot import file [" + absoluteFilenamePath + "].";
            return ServiceUtil.returnError(msg);
        }


        Map<String, Object> srvCtxMap = new HashMap<>();
        srvCtxMap.put("filename", absoluteFilenamePath);
        srvCtxMap.put("login.username", username);
        srvCtxMap.put("login.password", password);
        srvCtxMap.put("txTimeout", txTimeout);

        Map<String, Object> entityImportResultMap = null;

        try {
            entityImportResultMap = dispatcher.runSync("entityImport", srvCtxMap);
        } catch (GenericServiceException e ) {
            String msg = "Error running service entityImport on file " + absoluteFilenamePath + " in date " + nowDateStr + ". Msg => " + e.getMessage();
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnError(msg);
        }

        if (!ServiceUtil.isSuccess(entityImportResultMap)) {
            String errorMsg = ServiceUtil.getErrorMessage(entityImportResultMap);
            return ServiceUtil.returnError(errorMsg);
        }

        moved = MpFileUtil.moveToDirectory(absoluteFilenamePath, historyDirPath);
        Debug.logInfo("=== Moved " + absoluteFilenamePath + "to " + historyDirPath + "? " + moved, MODULE);

        returnMap = ServiceUtil.returnSuccess("File " + absoluteFilenamePath + " imported correctly in date " + nowDateStr);
        return returnMap;

    } //end method
    
 
    
} //end class
