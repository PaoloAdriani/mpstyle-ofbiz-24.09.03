package ecommerce

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtilProperties

def performCategoryFilterSearch() {

    String productCategoryId = parameters.productCategoryId
    Map filterFeatureMap = parameters.filterFeatureMap
    String productStoreId = parameters.productStoreId
    String prodCatalogId = parameters.prodCatalogId
    boolean limitView = parameters.limitView ?: false
    int defaultViewSize = parameters.defaultViewSize
    boolean filterOutOfStockVariants = parameters.filterOutOfStockVariants ?: false
    String MPSTYLE_SYSTEM_RESOURCE_ID = "mpstyle"

    def returnMessage = ""
    /*<K,V> : <featureTypeId, List<featureValueId>>: creation with HashSet to have unique values
     * but then converted to List before returning, to be easier to handle on the UI side
     */
    Map appliedFiltersByFeatureType = [:].withDefault { new HashSet<String>() }

    /* Read some configuration parameters to drive the filtering priorities among featureTypes and the logical
       operator to apply (OR vs AND) when multiple feature values are selected.
       Default operator: OR;
       Filtering priority: only if operator is AND
     */
    String filterOperator = EntityUtilProperties.getPropertyValue(MPSTYLE_SYSTEM_RESOURCE_ID, "catalog.filterOperator", "OR", delegator)
    String featureTypesFilterPrio = EntityUtilProperties.getPropertyValue(MPSTYLE_SYSTEM_RESOURCE_ID, "catalog.featureTypesFilterPrio", "", delegator)
    String ecFilterParentFeatureCategoryId = EntityUtilProperties.getPropertyValue(MPSTYLE_SYSTEM_RESOURCE_ID, "catalog.ecFilterParentFeatureCategoryId", "EC_FILTER_PFCAT", delegator)

    String []featureTypesFilterPrioArr = featureTypesFilterPrio ? featureTypesFilterPrio.split(",") : []
    List<String> featureTypesFilterPrioList = featureTypesFilterPrioArr ? featureTypesFilterPrioArr.toList() : []

    /* If operator is AND and is not defined a priority among feature types for filtering build
     * the list with the keys (featureTypeId) of the filterFeatureMap, to apply the filtering on all the feature types in the filter, without any priority
     */
    if (filterOperator.equalsIgnoreCase("AND") && featureTypesFilterPrioList.isEmpty()) {
        featureTypesFilterPrioList = filterFeatureMap.keySet().toList()

        /* TODO check on featureTypes existence
        def featureTypes = from('ProductFeatureType').where(EntityCondition.makeCondition('productFeatureTypeId', EntityOperator.IN, featureTypesFilterPrioList)).cache(true).queryList()
        */
    }

    logInfo("[performCategoryFilterSearch]: filterOperator = ${filterOperator}, featureTypesFilterPrioList = ${featureTypesFilterPrioList}")

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

        //Get all the features application for the products in the category, to have them in cache for the filtering logic later
        List<String> productIdList = productCategoryMembers.collect() { it.productId }
        List<GenericValue> productFeaturesAndAppl = from("ProductFeatureAndAppl")
                .where(EntityCondition.makeCondition('productId', EntityOperator.IN, productIdList))
                .cache(true)
                .filterByDate()
                .queryList()

        //Build an index of the features application by featureTypeId and featureId,
        // to be able to easily retrieve the products matching the filter criteria in the next steps
        Map featureApplIndex = [:].withDefault { [:].withDefault { new HashSet<>() } }
        productFeaturesAndAppl.each { gv ->
            featureApplIndex[gv.productFeatureTypeId][gv.productFeatureId].add(gv.productId)
        }
        //Description to be displayed in the UI as applied filter value
        Map featureValueDescriptionMap = productFeaturesAndAppl.collectEntries { gv -> [(gv.productFeatureId) :  (gv.description ?: gv.productFeatureId)] }

        /* Build a map of productFeatureId with the related productFeatureCategoryId to be able to check if a ProductFeature
         * belongs to a feature category. This map will be used to populate the appliedFiltersByFeatureType map with the
         * feature category description instead of the feature description, in case the filter is applied on a feature
         * category and not on a specific feature
         */
        Map featureAndRelatedFeatureCategoryMap = productFeaturesAndAppl.collectEntries { gv -> [(gv.productFeatureId) : gv.productFeatureCategoryId] }

        /* Retrieve the feature categories with a specific parent category
           in order to display them as filter options in the UI, and build a map of the feature values to be displayed for each feature type in the UI
         */
        List<GenericValue> filterFeatureCategories = from("ProductFeatureCategory")
                .where('parentCategoryId', ecFilterParentFeatureCategoryId)
                .cache(true)
                .queryList()

        Map filterFeatureCategoryMap = [:].withDefault { "N/A" }
        if (filterFeatureCategories) {
            filterFeatureCategories.collectEntries { gv -> [(gv.productFeatureCategoryId): gv.description] }
        }

        //Expand the filterFeatureMap checking if values passed in are featureId or featureCategoryId, and in this case retrieve the related featureIds to be used for filtering
        Map<String, List<String>> expandedFilterFeatureMap = [:].withDefault { [] }
        List featureCategoryIdList = []
        filterFeatureMap.each { featureTypeId, featureValueIdList ->
            featureValueIdList.each { featureValueId ->
                if (featureValueId.startsWith("PFC_")) { //this is a Product Feature Category
                    String featureCategoryId = featureValueId.substring("PFC_".length())
                    featureCategoryIdList.add(featureCategoryId)
                    Set<String> featureIdsWithCategory = productFeaturesAndAppl.findAll { gv -> gv.productFeatureCategoryId == featureCategoryId && gv.productFeatureTypeId == featureTypeId }
                            .collect { it.productFeatureId }
                            .toSet()
                    expandedFilterFeatureMap[featureTypeId].addAll(featureIdsWithCategory)
                } else if (featureValueId.startsWith("PF_")) {
                    //this is a Product Feature: simply substring to remove the "PF_" prefix and keep the featureId as value for filtering
                    expandedFilterFeatureMap[featureTypeId].add(featureValueId.substring("PF_".length()))
                }
            }
        }

        Set<String> baseCategoryMemberProductIdSet = productCategoryMembers.collect { it.productId }.toSet()
        Set<String> filteredPcmSet = null

        if (filterOperator.equalsIgnoreCase("AND")) {
            logInfo("[performCategoryFilterSearch]: applying AND logic among filter values, with filtering priority among feature types: ${featureTypesFilterPrioList}")
            filteredPcmSet = new HashSet<>(baseCategoryMemberProductIdSet)
            featureTypesFilterPrioList.each { featureTypeId ->
                List featureValueIdList = expandedFilterFeatureMap.get(featureTypeId)
                if (featureValueIdList) {
                    Set<GenericValue> matchingProductsByFeatureType = new HashSet<>()
                    featureValueIdList.each { productFeatureId ->
                        matchingProductsByFeatureType.addAll(featureApplIndex[featureTypeId]?.get(productFeatureId) ?: Collections.emptySet())
                        String relatedFeatureCategoryId = featureAndRelatedFeatureCategoryMap[productFeatureId]

                        String filterFeatureDescription = featureCategoryIdList.contains(relatedFeatureCategoryId) ? filterFeatureCategoryMap[relatedFeatureCategoryId] : featureValueDescriptionMap[productFeatureId]
                        appliedFiltersByFeatureType[featureTypeId].add(filterFeatureDescription)
                    }
                    filteredPcmSet.retainAll(matchingProductsByFeatureType)
                }
            }

        } else if (filterOperator.equalsIgnoreCase("OR")) {
            logInfo("[performCategoryFilterSearch]: applying OR logic among filter values")
            filteredPcmSet = new HashSet<>()
            expandedFilterFeatureMap.each { featureTypeId, featureValueIdList ->
                featureValueIdList.each { productFeatureId ->
                    Set<GenericValue> matchingProductsByFeatureType = featureApplIndex[featureTypeId][productFeatureId] ?: Collections.emptySet()
                    if (matchingProductsByFeatureType) {
                        filteredPcmSet.addAll(matchingProductsByFeatureType)
                        appliedFiltersByFeatureType[featureTypeId].add(featureValueDescriptionMap[productFeatureId])
                    }
                }
            }
        } else {
            logWarning("[performCategoryFilterSearch]: no valid filterOperator defined, skipping filtering and returning all the products in the category")
            filteredPcmSet = baseCategoryMemberProductIdSet
        }//end if filterOperator

        if (filteredPcmSet && !filteredPcmSet.isEmpty()) {
            filteredProductCategoryMembers = productCategoryMembers.findAll { gv ->
                filteredPcmSet.contains(gv.getString("productId"))
            }
            returnMessage = "Found ${filteredProductCategoryMembers.size()} product category members matching the filter criteria for category ${productCategoryId}."
        } else {
            returnMessage = "No products found matching the filter criteria for category ${productCategoryId}."
        }

    } else {
        filteredProductCategoryMembers = []
        returnMessage = "No product category members found for category ${productCategoryId}. Nothing to filter."//end if productCategoryMembers
    }

    Map returnMap = success(returnMessage)
    returnMap.filteredProductCategoryMembers = new ArrayList(filteredProductCategoryMembers)
    returnMap.appliedFiltersByFeatureType = appliedFiltersByFeatureType
    returnMap.filteredProductCategoryId = productCategoryId
    return returnMap
}
