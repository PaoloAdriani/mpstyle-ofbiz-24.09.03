/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;

import java.util.ArrayList;
import java.util.List;


/**
 *
 * @author equake58
 */
public class MpStyleShipmentUtil {
    
    public static final String module = MpStyleShipmentUtil.class.getName();
    
    private final static String LOGISTIC_PARTY_ROLE = "SHIPMENT_CLERK";

    /**
     * 
     * @param partyId
     * @param delegator
     * @return 
     */
    public static boolean checkPartyShipmentRole(String partyId, Delegator delegator) {
        
        return checkPartyRole(partyId, LOGISTIC_PARTY_ROLE, delegator);
        
    }
    
    /**
     * 
     * @param partyId
     * @param checkRoleTypeId
     * @param delegator
     * @return 
     */
    public static boolean checkPartyRole(String partyId, String checkRoleTypeId, Delegator delegator) {
        
        GenericValue party = null;
        List<GenericValue> partyRoles = null;
        boolean hasRole = false;
        
        try {
            party = delegator.findOne("Party", UtilMisc.toMap("partyId", partyId), false);
            partyRoles = party.getRelated("PartyRole", null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError("Error in retrieving party with id ["+partyId+"].", module);
            Debug.logError(gee.getMessage(), module);
            return false;
        }
        
        for(GenericValue role : partyRoles) {
            
            String _roleTypeId = (String) role.get("roleTypeId");
            
            if(checkRoleTypeId.equals(_roleTypeId)) {
                hasRole = true;
                break;
            }
            
        }
        
        return hasRole;
        
    }
    
    public static List<String> getFacilityAssocParty(String facilityId, Delegator delegator) {
        return getFacilityAssocPartyWithRole(facilityId, null, delegator);
    }
    
    /**
     * Return all the parties associated to a facility with a given role type.
     * If no role type is provided all the associations will be returned.
     * @param facilityId
     * @param checkRoleTypeId
     * @param delegator
     * @return 
     */
    public static List<String> getFacilityAssocPartyWithRole(String facilityId, String checkRoleTypeId, Delegator delegator) {
        
        List<String> facilityPartyRoleList = null;
        
        GenericValue facility = null;
        List<GenericValue> facilityParties = null;
        
        try {
            facility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", facilityId), false);
            facilityParties = facility.getRelated("FacilityParty", null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError("Error in retrieving facility with id ["+facilityId+"].", module);
            Debug.logError(gee.getMessage(), module);
            return null;
        }
        
        facilityParties = EntityUtil.filterByDate(facilityParties);
        
        if(UtilValidate.isNotEmpty(facilityParties)) {
            
            facilityPartyRoleList = new ArrayList<>();
            
            for(GenericValue facilityParty : facilityParties) {
                
                if(checkRoleTypeId == null) {
               
                    facilityPartyRoleList.add((String) facilityParty.get("partyId"));
                    
                }else{
                    
                    String _facilityRole = (String) facilityParty.get("roleTypeId");
                    
                    if(checkRoleTypeId.equals(_facilityRole)) {
                        facilityPartyRoleList.add((String) facilityParty.get("partyId"));
                    }
                    
                }
            }
            
        }
        
        return facilityPartyRoleList;
    }
    
    
    public static List<String> getPartyAssocFacilities(String partyId, Delegator delegator) {
        return getPartyAssocFacilitiesWithRole(partyId, null, delegator);
    }
    
    
    
    /**
     * Return all the facilities associated to the input party with a given role type.
     * If no role type is provided all the associations will be returned.
     * @param partyId
     * @param checkRoleTypeId
     * @param delegator
     * @return 
     */
    public static List<String> getPartyAssocFacilitiesWithRole(String partyId, String checkRoleTypeId, Delegator delegator) {
        
        List<String> facilityRoleList = null;
        
        GenericValue party = null;
        List<GenericValue> partyFacilities = null;
        
        try {
            party = delegator.findOne("Party", UtilMisc.toMap("partyId", partyId), false);
            partyFacilities = party.getRelated("FacilityParty", null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError("Error in retrieving party with id ["+partyId+"].", module);
            Debug.logError(gee.getMessage(), module);
            return null;
        }
        
        
        
        partyFacilities = EntityUtil.filterByDate(partyFacilities);
        
        if(UtilValidate.isNotEmpty(partyFacilities)) {
            
            facilityRoleList = new ArrayList<>();
            
            for(GenericValue partyFacility : partyFacilities) {
                
                if(checkRoleTypeId == null) {
               
                    facilityRoleList.add((String) partyFacility.get("facilityId"));
                    
                }else{
                    
                    String _facilityRole = (String) partyFacility.get("roleTypeId");
                    
                    if(checkRoleTypeId.equals(_facilityRole)) {
                        facilityRoleList.add((String) partyFacility.get("facilityId"));
                    }
                    
                }
                
            }
            
        }
        
        
        return facilityRoleList;
        
        
    }
    
    /**
     * 
     * @param partyClassificationGroupId
     * @param delegator
     * @return 
     */
    public static List<GenericValue> getShipmentClerkPartiesFromClassGroup(String partyClassificationGroupId, Delegator delegator) {
        
        List<GenericValue> shipmentClerkParties = new ArrayList<>();
        
        
        if(partyClassificationGroupId == null) {
            return shipmentClerkParties;
        }
        
        
        List<GenericValue> partyClassification = null;
        
        try {
            
            EntityCondition classCond = EntityCondition.makeCondition("partyClassificationGroupId", EntityOperator.EQUALS, partyClassificationGroupId);
            
            partyClassification = delegator.findList("PartyClassification", classCond, null, null, null, false);
            
        }catch(GenericEntityException gee) {
            Debug.logError(gee, "Error in retrieving PartyClassification for PartyClassificationGroup with id ["+partyClassificationGroupId, module);
            return shipmentClerkParties;
        }
        
        
        if(partyClassification != null) {
            partyClassification = EntityUtil.filterByDate(partyClassification);
            
            if(UtilValidate.isNotEmpty(partyClassification)) {
                
                for(GenericValue partyClass : partyClassification) {
                    
                    GenericValue _party = null;
                    try {
                        _party = partyClass.getRelatedOne("Party", false);
                    } catch (GenericEntityException ex) {
                        Debug.logError(ex, "Error in retrieving PartyGroup relation for party id ["+partyClass.getString("partyId"), module);
                        continue;
                    }
                    
                    if(_party != null && !shipmentClerkParties.contains(_party)) {
                        shipmentClerkParties.add(_party);
                        
                    }
                    
                }
                
            }
            
        }
        
        return shipmentClerkParties;
        
    }
    
    /**
     * Method that returns a list of facility for a product store considering
     * the one inventory facility flag on the store
     * @param productStoreId
     * @param delegator
     * @return 
     */
    public static List<GenericValue> getProductStoreFacilities(String productStoreId, Delegator delegator) {
        
        List<GenericValue> facilityList = null;
        GenericValue productStore = null;
        
        if(productStoreId == null || UtilValidate.isEmpty(productStoreId)) {
            Debug.logError("Product Store Id is null or empty. Return empty facility list.", module);
            return new ArrayList<>();
        }
        
                
        try {
            productStore = delegator.findOne("ProductStore", UtilMisc.toMap("productStoreId", productStoreId), true);
        }catch(GenericEntityException gee) {
            String msg = "Error in retrieving ProductStore with id ["+productStoreId+"]. "+gee.getMessage();
            Debug.logError(msg, module);
            return new ArrayList<>();
        }
        
        if(productStore != null) {
            
            facilityList = new ArrayList<>();
            
            String oneInventoryFacility = (String) productStore.get("oneInventoryFacility");
            
            //use primary facility id on the store
            if("Y".equals(oneInventoryFacility)) {
                
                
                GenericValue facility = null;
                
                try {
                    facility = productStore.getRelatedOne("Facility", true);
                } catch (GenericEntityException ex) {
                    Debug.logError(ex.getMessage(), module);
                    return facilityList;
                }
            
                facilityList.add(facility);
                
            }else{
                
                //Retrieve all the facilities associated to the store
                List<GenericValue> storeFacilityList = null;
                
                try {
                    storeFacilityList = delegator.findList("ProductStoreFacility", EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, productStoreId), null, UtilMisc.toList("sequenceNum"), null, false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee.getMessage(), module);
                    return facilityList;
                }
                
                //Filter out dated values
                storeFacilityList = EntityUtil.filterByDate(storeFacilityList);
                
                if(UtilValidate.isNotEmpty(storeFacilityList)) {
                    
                    for(GenericValue storeFacility : storeFacilityList) {
                        
                        GenericValue facility = null;
                        
                        try {
                            facility = storeFacility.getRelatedOne("Facility", true);
                        }catch(GenericEntityException gee) {
                            Debug.logError(gee.getMessage(), module);
                            return facilityList;
                        }
                        
                        facilityList.add(facility);
                    }
                    
                }else{
                //if store facility list is empty use the store primary facility
                    
                    GenericValue facility = null;
                
                    try {
                        facility = productStore.getRelatedOne("Facility", true);
                    } catch (GenericEntityException ex) {
                        Debug.logError(ex.getMessage(), module);
                        return facilityList;
                    }

                    facilityList.add(facility);
                }
                
            }
            
        }
        
        return facilityList;
        
    }

    
}//end class
