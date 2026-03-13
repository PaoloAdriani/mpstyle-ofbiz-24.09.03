import org.apache.ofbiz.product.catalog.CatalogWorker
import org.apache.ofbiz.product.store.ProductStoreWorker

productCategoryId = request.getAttribute('productCategoryId') ?: parameters.category_id
category = from('ProductCategory').where('productCategoryId', productCategoryId).cache(true).queryOne()

/*<K,V> K = featureType (COLOR,SIZE,...),
V = value [FEATURE_ID:DESCRIPTION]
Example: [COLOR:[00001:RED, 00002:BLUE]], [SIZE:[S:SMALL, M:MEDIUM]],...)
 */
Map<String, Map<String, String>> filterMap = new HashMap<>()

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

    if (productCategoryMembers) {
        //Get the feature types for the category members
        for (productCategoryMember in productCategoryMembers) {
            List<String> featureValueList = null
            productId = productCategoryMember.productId
            productFeatures = from('ProductFeatureAndAppl').where('productId', productId).cache(true).filterByDate().queryList()
            for (productFeature in productFeatures) {
                featureTypeId = productFeature.productFeatureTypeId
                featureId = productFeature.productFeatureId
                featureDescription = productFeature.description ?: productFeature.productFeatureId
                if (filterMap.containsKey(featureTypeId)) {
                    Map featureValueMap = filterMap.get(featureTypeId)
                    if (!featureValueMap.containsKey(featureId)) {
                        featureValueMap.put(featureId, featureDescription)
                        filterMap.put(featureTypeId, featureValueMap)
                    }
                } else {
                    def featureValueMap = [:]
                    featureValueMap.put(featureId, featureDescription)
                    filterMap.put(featureTypeId, featureValueMap)
                }
            }
        }
    }

} else {
    logWarning("ProductCategoryFilters.groovy: No category found for productCategoryId = ${productCategoryId}")
}

context.filterMap = filterMap