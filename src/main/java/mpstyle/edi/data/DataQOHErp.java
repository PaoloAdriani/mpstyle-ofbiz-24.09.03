package mpstyle.edi.data;

import java.math.BigDecimal;

public class DataQOHErp {

    private String facility;
    private String article;
    private BigDecimal availability;

    public DataQOHErp(String facility, String article, BigDecimal availability) {
            this.facility = facility;
            this.article = article;
            this.availability = availability;
    }
    public String getFacility() {
            return facility;
    }
    public void setFacility(String facility) {
            this.facility = facility;
    }
    public String getArticle() {
            return article;
    }
    public void setArticle(String article) {
            this.article = article;
    }
    public BigDecimal getAvailability() {
            return availability;
    }
    public void setAvailability(BigDecimal availability) {
            this.availability = availability;
    }
	

} //end class
