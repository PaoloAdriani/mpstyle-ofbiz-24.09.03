package mpstyle.util.fixedlength;

import org.apache.ofbiz.base.util.Debug;

import java.io.*;


/**
 *
 * @author Nicola Mazzoni
 * 
 * Crea un file di testo database a lunghezza fissa
 */
public class FileLunghezzaFissaWriter {
    
    public static final String module = FileLunghezzaFissaWriter.class.getName();
    
    private int LunghezzaRecord = 0;
    private FileWriter fw;
    private char[] buffer;
    
    /**
     *
     * @param LunghezzaRecord La lunghezza totale del record del database
     * @param PathFileOutput Il percorso completo di nome del file del file di testo di output (es:
     * c:/export/mio_file.txt)
     */    
    public FileLunghezzaFissaWriter(int LunghezzaRecord, String PathFileOutput) {
        
        try {
            this.LunghezzaRecord = LunghezzaRecord;
            fw = new FileWriter(PathFileOutput);
        } catch (IOException e) {
            Debug.logError(e.getMessage(), module);
        }
        
    }
    
    /**
     *
     * @param campo Il valore del campo da scrivere
     * @param posizione La posizione (a partire dalla 1) in cui scrivere il valore
     */    
    public void setCampo(String campo, int posizione) {
        
        posizione--;
        
        for (int x=0; x< campo.length();x++) {
            buffer[posizione + x] = campo.charAt(x);
        }
        
    }
    
    /**
     * Metodo che prepara un record vuoto prima di settare il valore dei campi
     */    
    public void addNewRecord() {
        
        buffer = new char[this.LunghezzaRecord];
        
        for (int x=0; x<this.LunghezzaRecord; x++) {
            buffer[x] = ' ';
        }
    }
    
    /**
     * Scrive il record sul file
     */    
    public void writeRecord() {
        
        try {
            String str = new String(buffer);
            fw.write(str + '\r' + '\n');
            fw.flush();
        } catch (IOException e) {
            Debug.logError(e.getMessage(), module);
            
        }
    }
    
    /**
     * (opzionale) chiude il file
     */    
    public void closeFile() {
        
        try {
            fw.close();
        } catch (IOException e) {
           Debug.logError(e.getMessage(), module);
        }
    }
    
} //end class
