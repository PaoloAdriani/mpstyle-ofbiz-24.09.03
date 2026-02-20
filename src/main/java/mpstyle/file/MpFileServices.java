package mpstyle.file;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import mpstyle.util.file.MpFileUtil;

public class MpFileServices {

    public static final String MODULE = MpFileServices.class.getName();

    public static Map<String, Object> importFile(String absoluteFilenamePath, String historyDirPath, Integer txTimeout,
                                                 String username, String password, String nowDateStr, LocalDispatcher dispatcher) {

        Map<String, Object> returnMap = null;
        boolean moved = false;

        Debug.logInfo("=== Importing file " + absoluteFilenamePath + " with service entityImport ===", MODULE);

        if (absoluteFilenamePath == null || absoluteFilenamePath.trim().isEmpty()) {
            String msg = "File name to import is null or empty. Cannot import.";
            return ServiceUtil.returnError(msg);
        }

        if (historyDirPath == null || historyDirPath.trim().isEmpty()) {
            String msg = "History directory path is null or empty. Cannot import file [" + absoluteFilenamePath + "].";
            return ServiceUtil.returnError(msg);
        }


        Map<String, Object> srvCtxMap = new HashMap<>();
        srvCtxMap.put("filename", absoluteFilenamePath);
        srvCtxMap.put("login.username", username);
        srvCtxMap.put("login.password", password);
        srvCtxMap.put("txTimeout", txTimeout);

        Map<String, Object> entityImportResultMap = null;

        try {
            entityImportResultMap = dispatcher.runSync("entityImport", srvCtxMap);
        } catch (GenericServiceException e ) {
            String msg = "Error running service entityImport on file " + absoluteFilenamePath + " in date " + nowDateStr + ". Msg => " + e.getMessage();
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnError(msg);

            //String mailMsg = "Method: importFile - Service: entityImport - Service Status: FAILED - Processed File: "+ filename.trim()+" - Run Time: "+ nowDateString;

            //sendServiceStatusEmail(mailMsg, username, password);

        }

        if (!ServiceUtil.isSuccess(entityImportResultMap)) {
            String errorMsg = ServiceUtil.getErrorMessage(entityImportResultMap);
            return ServiceUtil.returnError(errorMsg);
        }

        moved = MpFileUtil.moveToDirectory(absoluteFilenamePath, historyDirPath);
        Debug.logInfo("=== Moved " + absoluteFilenamePath + "to " + historyDirPath + "? " + moved, MODULE);

        returnMap = ServiceUtil.returnSuccess("File " + absoluteFilenamePath + " imported correctly in date " + nowDateStr);
        return returnMap;

    }

} //end class
