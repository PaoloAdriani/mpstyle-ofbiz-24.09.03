package ecommerce

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

def performCategoryFilterSearch() {

    String productCategoryId = parameters.productCategoryId
    Map filterFeatureMap = parameters.filterFeatureMap
    String productStoreId = parameters.productStoreId
    String prodCatalogId = parameters.prodCatalogId
    boolean limitView = parameters.limitView ?: false
    int defaultViewSize = parameters.defaultViewSize
    def returnMessage = ""
    /*<K,V> : <featureTypeId, List<featureValueId>>: creation with HashSet to have unique values
     * but then converted to List before returning, to be easier to handle on the UI side
     */
    Map appliedFiltersByFeatureType = [:].withDefault { new HashSet<String>() }

    //Get product category members with the same logic as categorydetail screen, then apply filters on them
    def catAndMemberMap = [productCategoryId: productCategoryId,
                           defaultViewSize: defaultViewSize,
                           limitView: limitView]
    catAndMemberMap.put('prodCatalogId', prodCatalogId)
    catAndMemberMap.put('checkViewAllow', true)
    // Prevents out of stock product to be displayed on site
    if (productStoreId) {
        catAndMemberMap.put('productStoreId', productStoreId)
    }
    catAndMemberMap.put('orderByFields', ['sequenceNum', 'productId'])
    def catAndMemberResult = runService('getProductCategoryAndLimitedMembers', catAndMemberMap)
    productCategory = catAndMemberResult.productCategory
    productCategoryMembers = catAndMemberResult.productCategoryMembers

    List<GenericValue> filteredProductCategoryMembers = []
    if (productCategoryMembers) {
        for (productCategoryMember in productCategoryMembers) {
            productId = productCategoryMember.productId
            def featureTypeMatchCount = 0
            for (featureTypeId in filterFeatureMap.keySet()) {
                List featureValueIdList = filterFeatureMap.get(featureTypeId)
                if (featureValueIdList) {
                    EntityCondition featureTypeCond = EntityCondition.makeCondition(EntityOperator.AND,
                            EntityCondition.makeCondition('productId', EntityOperator.EQUALS, productId),
                            EntityCondition.makeCondition('productFeatureTypeId', EntityOperator.EQUALS, featureTypeId),
                            EntityCondition.makeCondition('productFeatureId', EntityOperator.IN, featureValueIdList))
                    productFeatures = from('ProductFeatureAndAppl').where(featureTypeCond).cache(true).filterByDate().queryList()
                    if (productFeatures && !productFeatures.isEmpty()) {
                        featureTypeMatchCount++
                        List featureValueDescriptionList = productFeatures.collect { it.description ?: it.productFeatureId }
                        /* This works along with the .withDefault { new HashSet<>() } I used to initialize the appliedFiltersByFeatureType map,
                         * to keep track of the filters that actually matched for the products in the category,
                         * and that I can then use to display only the relevant filters on the UI
                         */
                        appliedFiltersByFeatureType[featureTypeId].addAll(featureValueDescriptionList)
                    }
                }
            }
            //If I have a match for at least one of the feature types in the filter, then I keep the product category member in the result
            if (featureTypeMatchCount > 0) {
                if (!filteredProductCategoryMembers) {
                    filteredProductCategoryMembers = []
                }
                filteredProductCategoryMembers.add(productCategoryMember)
            }
        }
        // Convert the sets to lists before returning, to be easier to handle on the UI side
        if (appliedFiltersByFeatureType && !appliedFiltersByFeatureType.isEmpty()) {
            appliedFiltersByFeatureType = appliedFiltersByFeatureType.collectEntries { k, v -> [ (k) : new ArrayList(v) ] }
        }
        returnMessage = "Found ${filteredProductCategoryMembers.size()} product category members matching the filter criteria for category ${productCategoryId}."
    } else {
        filteredProductCategoryMembers = []
        returnMessage = "No product category members found for category ${productCategoryId}. Nothing to filter."
    }

    Map returnMap = success(returnMessage)
    returnMap.filteredProductCategoryMembers = new ArrayList(filteredProductCategoryMembers)
    returnMap.appliedFiltersByFeatureType = appliedFiltersByFeatureType
    returnMap.filteredProductCategoryId = productCategoryId
    return returnMap
}