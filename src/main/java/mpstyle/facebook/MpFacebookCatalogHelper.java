package mpstyle.facebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.ofbiz.base.util.Debug;

public class MpFacebookCatalogHelper
{
    public static final String module = MpFacebookCatalogHelper.class.getName();

    public static FacebookItem createFacebookItem(String productId,String title,String description,String imageLink, String productLink,String stockStatus,String productType,String productCategoryId, String listPriceCurrency, String salePriceCurrency,
    String brand, String googleProductCategoryId, Map<String,String> shippingEstimateMap)
    {
	    String gpcDesc = findGoogleProductCategoryDescription(googleProductCategoryId);
	    FacebookItem fi = new FacebookItem();
	    fi.setId(productId);
	    fi.setTitle(capitalize(title.toLowerCase()));
	    fi.setDescription(capitalize(description.toLowerCase()));
	    fi.setLink(productLink);
	    fi.setCondition("new");
	    fi.setPrice(listPriceCurrency);
	    if(!salePriceCurrency.equals(listPriceCurrency))
	    {
	    	fi.setSale_price(salePriceCurrency);
	    }
	    fi.setAvailability(stockStatus);
	    fi.setImage_link(imageLink);
	    fi.setBrand(brand);
	    fi.setGoogle_product_category(gpcDesc);
	    for (Map.Entry<String,String> entry : shippingEstimateMap.entrySet()) 
        {        	
            String key = entry.getKey();	
            String value = entry.getValue();
                    
			StringBuffer sb = new StringBuffer();
			sb.append(key).append(":::").append(value);

			fi.setShipping(sb.toString());
        }

		fi.setCustom_label_0(capitalize(productType.toLowerCase()));

		System.out.println("*******fi: "+fi.toString());
    
        return fi;
    }
    
    private static String findGoogleProductCategoryDescription(String googleProductCategoryId)
    {   
	    String resultString = null;
	    
        switch (googleProductCategoryId) 
        {
	    	case "1604":
	    		resultString = "Apparel & Accessories > Clothing";
	    		break;
	    	case "4171":
	    		resultString = "Casa e giardino > Biancheria";
	    		break;	
        
	    	default:
	    		break;
	    }
	    
        return resultString;
    }


    public static void createFacebookCatalogCsvFile(List<FacebookItem> facebookItemList, String outPath, String fileName) 
    {

		File csvOutputFile = new File(outPath + fileName);

		if (!csvOutputFile.exists()) {
			try {
				csvOutputFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		CsvMapper mapper = new CsvMapper();

		mapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);

		CsvSchema schema = CsvSchema.builder().setUseHeader(true).setColumnSeparator(';')
				.addColumn("id")
				.addColumn("title")
				.addColumn("description")
				.addColumn("link")
				.addColumn("condition")
				.addColumn("price")
				.addColumn("sale_price")
				.addColumn("availability")
				.addColumn("image_link")
				.addColumn("gtin")
				.addColumn("mpn")
				.addColumn("brand")
				.addColumn("google_product_category")
				.addColumn("shipping")
				.addColumn("custom_label_0")
				.build().withoutQuoteChar().withNullValue("");

		ObjectWriter writer = mapper.writerFor(FacebookItem.class).with(schema);

		try 
        {

			OutputStream outputStream = new FileOutputStream(csvOutputFile, false);

			writer.writeValues(outputStream).writeAll(facebookItemList);
		
        } catch (IOException e) {
			// TODO Auto-generated catch block
			Debug.logError(e, "Error into creating order file.", module);
		}

	}
    
    public static String capitalize(String str)
    {
        if(str == null || str.length()<=1) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
             
    }

		    

}