/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.edi.imp;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 * @author equake58
 */
public class MpAvailabilityHelper {
    
    public static final String MODULE = MpAvailabilityHelper.class.getName();
    
    /**
     * 
     * @param filename
     * @param processingError
     * @param procErrList
     * @param skipProdCount
     * @param errProdCount
     * @param totProdCount
     * @return 
     */
    public static Map<String, Object> buildReturnResultMap(String filename, Boolean processingError, List<String> procErrList, List<String> skipProdMsgList,  int skipProdCount, int errProdCount, int totProdCount) {
        
        Map<String, Object> _map = new HashMap<>();
        
        _map.put("filename", filename);
        _map.put("processError", processingError);
        _map.put("processingErrorList", procErrList);
        _map.put("skippedProductCount", skipProdCount);
        _map.put("skippedProductList", skipProdMsgList);
        _map.put("errorProductCount", errProdCount);
        _map.put("totalProductCount", totProdCount);
        
        return _map;
        
    }

    /**
     * 
     * @param barcodeValue
     * @param barcodeType
     * @param delegator
     * @return 
     */
    public static GenericValue getProductFromBarocde(String barcodeValue, String barcodeType, Delegator delegator) {
        
        GenericValue product = null;
        
        if(barcodeValue == null || barcodeValue.trim().isEmpty()) {
            Debug.logError("Barcode value is null or empty. Cannot get the related product.", MODULE);
            return null;
        }
        
        List<GenericValue> productGoodIdNumList = null;
        
        try {
            EntityCondition cond = EntityCondition.makeCondition(EntityOperator.AND,
                                    EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.EQUALS, barcodeType),
                                    EntityCondition.makeCondition("idValue", EntityOperator.EQUALS, barcodeValue));
            
            productGoodIdNumList = delegator.findList("GoodIdentification", cond, null, null, null, false);
            
        }catch(GenericEntityException gee) {
            Debug.logError("Error in retrieving GoodIdentification records for type [" + barcodeType + "] and value [" + barcodeValue + "]. Error is => " + gee.getMessage(), MODULE);
            return null;
        }
        
        if(productGoodIdNumList == null || productGoodIdNumList.isEmpty()) {
            Debug.logWarning("No product records found for barcode type [" + barcodeType + "] with value [" + barcodeValue + "].", MODULE);
            return null;
        }
        
        //The same barcode values is related to more than one product. Returning null.
        if(productGoodIdNumList.size() > 1) {
            Debug.logWarning("ATTENTION! More than one product association found for barcode type [" + barcodeType + "] with value [" + barcodeValue + "]. Returning null.", MODULE);
            return null;
        }
        
        product = EntityUtil.getFirst(productGoodIdNumList);
        
        return product;
        
    }
    
} //end class
