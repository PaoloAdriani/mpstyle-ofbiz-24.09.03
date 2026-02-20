package mpstyle.facebook;

public class FacebookItem 
{
	private String id;
	private String title;
	private String description;
	private String link;
	private String condition;
	private String price;
	private String sale_price;
	private String availability;
	private String image_link;
	private String gTin;
	private String mpn;
	private String brand;
	private String google_product_category;
	private String shipping;
	private String custom_label_0;
	
	public FacebookItem() {
		super();
	}

	public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getLink() {
		return link;
	}

	public String getCondition() {
		return condition;
	}

	public String getPrice() {
		return price;
	}

	public String getSale_price() {
		return sale_price;
	}

	public String getAvailability() {
		return availability;
	}

	public String getImage_link() {
		return image_link;
	}

	public String getgTin() {
		return gTin;
	}

	public String getMpn() {
		return mpn;
	}

	public String getBrand() {
		return brand;
	}

	public String getGoogle_product_category() {
		return google_product_category;
	}

	public String getShipping() {
		return shipping;
	}

	public String getCustom_label_0() {
		return custom_label_0;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public void setPrice(String price) {
		this.price = price;
	}

	public void setSale_price(String sale_price) {
		this.sale_price = sale_price;
	}

	public void setAvailability(String availability) {
		this.availability = availability;
	}

	public void setImage_link(String image_link) {
		this.image_link = image_link;
	}

	public void setgTin(String gTin) {
		this.gTin = gTin;
	}

	public void setMpn(String mpn) {
		this.mpn = mpn;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public void setGoogle_product_category(String google_product_category) {
		this.google_product_category = google_product_category;
	}

	public void setShipping(String shipping) {
		this.shipping = shipping;
	}

	public void setCustom_label_0(String custom_label_0) {
		this.custom_label_0 = custom_label_0;
	}

	@Override
	public String toString() {
		return "FacebookItem [id=" + id + ", title=" + title + ", description=" + description + ", link=" + link
				+ ", condition=" + condition + ", price=" + price + ", sale_price=" + sale_price + ", availability="
				+ availability + ", image_link=" + image_link + ", gTin=" + gTin + ", mpn=" + mpn + ", brand=" + brand
				+ ", google_product_category=" + google_product_category + ", shipping=" + shipping
				+ ", custom_label_0=" + custom_label_0 + "]";
	}

}
