/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.product;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;

/**
 *
 * @author equake58
 */
public class MpProductUtil {
    
    public static final String MODULE = MpProductUtil.class.getName();
    
    private final static int SEASON_LEN = 2;
    private final static int LINE_LEN = 1;
    
    /**
     * Return product season for a product id coded like:
     * SEASON: 2 chars
     * LINE: 1 char
     * ART: 6 char
     * COLOR: 3-5 CHARS
     * @param productId
     * @param delegator
     * @return 
     */
    public static String getProductSeason(String productId, Delegator delegator) {
        
        String season = null;
        
        if(productId == null || UtilValidate.isEmpty(productId.trim())) {
            Debug.logWarning("ProductId parameter is null or empty. Cannot retrieve product season.", MODULE);
            return null;
        }
        
        GenericValue product = null;
        
        try {
            product = delegator.findOne("Product", UtilMisc.toMap("productId", productId), false);
        }catch(GenericEntityException gee) {
            Debug.logError("Error in retrieving product with id [" + productId + "]. Error is => " + gee.getMessage(), MODULE);
            return null;
        }
        
        if(product != null) {
            season = productId.substring(0, SEASON_LEN);
        }
        
        return season;
        
    }
    
    /**
     * Return product line for a product id coded like:
     * SEASON: 2 chars
     * LINE: 1 char
     * ART: 6 char
     * COLOR: 3-5 CHARS
     * @param productId
     * @param delegator
     * @return 
     */
    public static String getProductLine(String productId, Delegator delegator) {
        
        String line = null;
        
        if(productId == null || UtilValidate.isEmpty(productId.trim())) {
            Debug.logWarning("ProductId parameter is null or empty. Cannot retrieve product line.", MODULE);
            return null;
        }
        
        GenericValue product = null;
        
        try {
            product = delegator.findOne("Product", UtilMisc.toMap("productId", productId), false);
        }catch(GenericEntityException gee) {
            Debug.logError("Error in retrieving product with id [" + productId + "]. Error is => " + gee.getMessage(), MODULE);
            return null;
        }
        
        if(product != null) {
            line = productId.substring(2, 2 + LINE_LEN);
        }
        
        return line;
        
    }
    
} //end class
