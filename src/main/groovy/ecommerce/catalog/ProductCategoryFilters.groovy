import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.product.catalog.CatalogWorker
import org.apache.ofbiz.product.store.ProductStoreWorker

productCategoryId = request.getAttribute('productCategoryId') ?: parameters.category_id
category = from('ProductCategory').where('productCategoryId', productCategoryId).cache(true).queryOne()

String MPSTYLE_SYSTEM_RESOURCE_ID = "mpstyle"

/*<K,V> K = featureType (COLOR,SIZE,...),
V = value [FEATURE_ID:DESCRIPTION]
Example: [COLOR:[00001:RED, 00002:BLUE]], [SIZE:[S:SMALL, M:MEDIUM]],...)
 */
Map<String, Map<String, String>> filterMap = new HashMap<>()

String ecFilterParentFeatureCategoryId = EntityUtilProperties.getPropertyValue(MPSTYLE_SYSTEM_RESOURCE_ID, "catalog.ecFilterParentFeatureCategoryId", "EC_FILTER_PFCAT", delegator)

if (category) {

    //Get product category members with the same logic as categorydetail screen, then apply filters on them
    def limitView = false
    def currentCatalogId = CatalogWorker.getCurrentCatalogId(request)
    def defaultViewSize = request.getAttribute('defaultViewSize') ?: modelTheme.getDefaultViewSize() ?: 20

    def catAndMemberMap = [productCategoryId: productCategoryId,
                           defaultViewSize: defaultViewSize,
                           limitView: limitView]
    catAndMemberMap.put('prodCatalogId', currentCatalogId)
    catAndMemberMap.put('checkViewAllow', true)
    // Prevents out of stock product to be displayed on site
    productStore = ProductStoreWorker.getProductStore(request)
    if (productStore) {
        catAndMemberMap.put('productStoreId', productStore.productStoreId)
    }
    catAndMemberMap.put('orderByFields', ['sequenceNum', 'productId'])
    def catAndMemberResult = runService('getProductCategoryAndLimitedMembers', catAndMemberMap)
    productCategoryMembers = catAndMemberResult.productCategoryMembers

    //NEW
    if (productCategoryMembers) {

        //Get all the features application for the products in the category, to have them in cache to extract the product feature types and values for the filters
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

        /* Retrieve the feature categories with a specific parent category
           in order to display them as filter options in the UI, and build a map of the feature values to be displayed for each feature type in the UI
         */
        List<GenericValue> filterFeatureCategories = from("ProductFeatureCategory")
                                                    .where('parentCategoryId', ecFilterParentFeatureCategoryId)
                                                    .cache(true)
                                                    .queryList()

        Set<String> filterFeatureCategoryIdSet = filterFeatureCategories.collect { it.productFeatureCategoryId }.toSet()
        Map filterFeatureCategoryMap = filterFeatureCategories.collectEntries { gv -> [(gv.productFeatureCategoryId) : gv.description] }

        for (productFeatureAndAppl in productFeaturesAndAppl) {
            featureTypeId = productFeatureAndAppl.productFeatureTypeId
            featureId = productFeatureAndAppl.productFeatureId
            featureDescription = productFeatureAndAppl.description ?: featureId
            featureCategoryId = productFeatureAndAppl.productFeatureCategoryId?: ''

            if (filterFeatureCategoryIdSet.contains(featureCategoryId)) {
                if (filterMap.containsKey(featureTypeId)) {
                    Map featureValueMap = filterMap.get(featureTypeId)
                    if (!featureValueMap.containsKey(featureCategoryId)) {
                        featureValueMap.put('PFC_' + featureCategoryId, filterFeatureCategoryMap[featureCategoryId] ?: featureCategoryId)
                        filterMap.put(featureTypeId, featureValueMap)
                    }
                } else {
                    def featureValueMap = [:]
                    featureValueMap.put('PFC_' + featureCategoryId, filterFeatureCategoryMap[featureCategoryId] ?: featureCategoryId)
                    filterMap.put(featureTypeId, featureValueMap)
                }
            } else {
                if (filterMap.containsKey(featureTypeId)) {
                    Map featureValueMap = filterMap.get(featureTypeId)
                    if (!featureValueMap.containsKey(featureId)) {
                        featureValueMap.put('PF_' + featureId, featureDescription)
                        filterMap.put(featureTypeId, featureValueMap)
                    }
                } else {
                    def featureValueMap = [:]
                    featureValueMap.put('PF_' + featureId, featureDescription)
                    filterMap.put(featureTypeId, featureValueMap)
                }
            }
        }
    }

} else {
    logWarning("ProductCategoryFilters.groovy: No category found for productCategoryId = ${productCategoryId}")
}

context.filterMap = filterMap