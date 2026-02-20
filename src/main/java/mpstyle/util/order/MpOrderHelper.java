/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.order;


import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;

/**
 *
 * @author equake58
 */
public class MpOrderHelper {
    
    public static final String MODULE = MpOrderHelper.class.getName();
    
    /**
     * Given an order item inventory reservation record, returns the reservation facility.
     * @param orderItemInventoryReservation
     * @param delegator
     * @return 
     */
    public static String getInventoryReservationFacility(GenericValue orderItemInventoryReservation, Delegator delegator) {
        
        String reservationFacilityId = null;
        
        if(orderItemInventoryReservation == null) {
            Debug.logError("Inventory reservation record is null or missing. Cannot return reservation facility.", MODULE);
            return null;
        }
        
        GenericValue _invItem = null;

        try {
            _invItem = orderItemInventoryReservation.getRelatedOne("InventoryItem", false);
        } catch (GenericEntityException ex) {
            String msg = "Error in retrieving InventoryItem relation for inventory reservation record ["+orderItemInventoryReservation+"]. Error is => " + ex.getMessage();
            Debug.logError(msg, MODULE);
            return null;
        }

        String _invFacilityId = (String) _invItem.get("facilityId");

        GenericValue _facility = null;

        try {
            _facility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", _invFacilityId), false);

        }catch(GenericEntityException gee) {
            String msg = "Error in retrieving facility with id ["+_invFacilityId+"]. Error is => " + gee.getMessage();
            Debug.logError(msg, MODULE);
            return null;
        }
        
       
        reservationFacilityId = (_facility != null) ? (String) _facility.get("facilityId") : null;
        
        return reservationFacilityId;
        
    }
    
} //end class
