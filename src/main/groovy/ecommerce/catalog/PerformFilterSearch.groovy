package ecommerce.catalog

import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.product.catalog.CatalogWorker
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.product.store.ProductStoreWorker
import org.apache.ofbiz.service.ServiceUtil

productCategoryId = parameters.fromProductCategoryId

Map featureTypeFilterMap = [:]
List<GenericValue> productCategoryFilteredMembers = null
Map appliedFiltersByFeatureType = [:]
def optionPrefix = "SRC_"

parameters.each { parm ->
    if (parm.key.startsWith(optionPrefix)) {
        String featureTypeId = parm.key.substring(optionPrefix.length())
        List<String> featureValueIdList = (parm.value instanceof Collection) ? parm.value : [parm.value]
        featureTypeFilterMap.put(featureTypeId, featureValueIdList)
    }
}

category = from('ProductCategory').where('productCategoryId', productCategoryId).cache(true).queryOne()

if (category) {
    // Prepare the service map for the filtering service
    limitView = false
    currentCatalogId = CatalogWorker.getCurrentCatalogId(request)
    defaultViewSize = request.getAttribute('defaultViewSize') ?: modelTheme.getDefaultViewSize() ?: 20
    def filteringMap = [productCategoryId: productCategoryId,
                        defaultViewSize  : defaultViewSize,
                        limitView        : limitView]
    filteringMap.put('prodCatalogId', currentCatalogId)
    filteringMap.put('checkViewAllow', true)
    // Prevents out of stock product to be displayed on site
    productStore = ProductStoreWorker.getProductStore(request)
    if (productStore) {
        filteringMap.put('productStoreId', productStore.productStoreId)
    }
    filteringMap.put("filterFeatureMap", featureTypeFilterMap)
    //perform filtering
    filteringResult = runService('performCategoryFilterSearch', filteringMap)
    if (ServiceUtil.isSuccess(filteringResult)) {
        logInfo("PerformFilterSearch.groovy: ${filteringResult.successMessage}")
    } else {
        logError("PerformFilterSearch.groovy: performCategoryFilterSearch service failed with message: ${filteringResult.errorMessage}")
    }
    productCategoryFilteredMembers = filteringResult?.filteredProductCategoryMembers
    appliedFiltersByFeatureType = filteringResult?.appliedFiltersByFeatureType
    filteredProductCategoryId = filteringResult?.filteredProductCategoryId
} else {
    logWarning("PerformFilterSearch.groovy: No category found for productCategoryId = ${productCategoryId}")
}

context.productCategoryFilteredMembers = productCategoryFilteredMembers
context.appliedFiltersByFeatureType = appliedFiltersByFeatureType
context.fromProductCategoryId = productCategoryId
