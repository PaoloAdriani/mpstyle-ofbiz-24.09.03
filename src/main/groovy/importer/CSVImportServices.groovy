package importer

import mpstyle.edi.imp.MpAvailabilityWorker
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.service.ServiceUtil

import mpstyle.util.file.MpFileUtil

/*
Elaborazione file csv disponibilità
 */
def elaborateCsvFileList() {

    String path = parameters.toreadpath
    String historyPath = parameters.historytowritepath
    String username = parameters.username
    String password = parameters.password

    println "userLogin **********************"+userLogin

    def resultMap = [:]
    def importedFiles = ""
    def notImportedFiles = ""
    def status = ""

    Date nowDate = UtilDateTime.nowDate()
    String nowDateString = UtilDateTime.toDateTimeString(nowDate)

    List<String> filesFromFolder = MpFileUtil.readAndSort(path)
    def totalFileCount = filesFromFolder.size()
    def importedFileCount = 0
    def notImportedFileCount = 0

    for(String filename : filesFromFolder) {

        Map<String, Object> importStatusMap = MpAvailabilityWorker.importAvailabilityCsvFile(filename, historyPath, username, password, userLogin, delegator, dispatcher)

        if (!ServiceUtil.isSuccess(importStatusMap)) {
            String msg = ServiceUtil.getErrorMessage(importStatusMap);
            logError(msg)
            notImportedFiles = notImportedFiles + filename.substring(filename.lastIndexOf("/") + 1) + ","
            notImportedFileCount++
            continue
        } else {
            importedFiles = importedFiles + filename.substring(filename.lastIndexOf("/") + 1) + ","
            importedFileCount++
        }

    }

    if (totalFileCount == importedFileCount) {
        status = "IMPORT SUCCESSFULL"
    } else if (totalFileCount == notImportedFileCount) {
        status = "IMPORT FAILED"
    } else {
        status = "IMPORT PARTIALLY SUCCESSFULL"
    }

    resultMap = ServiceUtil.returnSuccess()
    resultMap.status = "Status: " + status + " - Imported files [" + importedFiles + "] - Not imported files [" + notImportedFiles + "] - Run Time [" + nowDateString + "]"

    return resultMap

} //end elaborateCsvFileList
