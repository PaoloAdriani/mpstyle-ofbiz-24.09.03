package mpstyle.ws.order;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlRootElement(name = "item")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateOrderRequestItem 
{
	@XmlElement(name = "season-id")
    private String SeasonId;

    @XmlElement(name = "collection-id")
    private String CollectionId;

    @XmlElement(name = "product-id")
    private String ProductId;

    @XmlElement(name = "color-id")
    private String ColorId;

    private int size01;
    private int size02;
    private int size03;
    private int size04;
    private int size05;
    private int size06;
    private int size07;
    private int size08;
    private int size09;
    private int size10;
    private int size11;
    private int size12;
    private int size13;
    private int size14;
    private int size15;
    private int size16;

    private double price;

	public String getSeasonId() {
		return SeasonId;
	}

	public void setSeasonId(String seasonId) {
		SeasonId = seasonId;
	}

	public String getCollectionId() {
		return CollectionId;
	}

	public void setCollectionId(String collectionId) {
		CollectionId = collectionId;
	}

	public String getProductId() {
		return ProductId;
	}

	public void setProductId(String productId) {
		ProductId = productId;
	}

	public String getColorId() {
		return ColorId;
	}

	public void setColorId(String colorId) {
		ColorId = colorId;
	}

	public int getSize01() {
		return size01;
	}

	public void setSize01(int size01) {
		this.size01 = size01;
	}

	public int getSize02() {
		return size02;
	}

	public void setSize02(int size02) {
		this.size02 = size02;
	}

	public int getSize03() {
		return size03;
	}

	public void setSize03(int size03) {
		this.size03 = size03;
	}

	public int getSize04() {
		return size04;
	}

	public void setSize04(int size04) {
		this.size04 = size04;
	}

	public int getSize05() {
		return size05;
	}

	public void setSize05(int size05) {
		this.size05 = size05;
	}

	public int getSize06() {
		return size06;
	}

	public void setSize06(int size06) {
		this.size06 = size06;
	}

	public int getSize07() {
		return size07;
	}

	public void setSize07(int size07) {
		this.size07 = size07;
	}

	public int getSize08() {
		return size08;
	}

	public void setSize08(int size08) {
		this.size08 = size08;
	}

	public int getSize09() {
		return size09;
	}

	public void setSize09(int size09) {
		this.size09 = size09;
	}

	public int getSize10() {
		return size10;
	}

	public void setSize10(int size10) {
		this.size10 = size10;
	}

	public int getSize11() {
		return size11;
	}

	public void setSize11(int size11) {
		this.size11 = size11;
	}

	public int getSize12() {
		return size12;
	}

	public void setSize12(int size12) {
		this.size12 = size12;
	}

	public int getSize13() {
		return size13;
	}

	public void setSize13(int size13) {
		this.size13 = size13;
	}

	public int getSize14() {
		return size14;
	}

	public void setSize14(int size14) {
		this.size14 = size14;
	}

	public int getSize15() {
		return size15;
	}

	public void setSize15(int size15) {
		this.size15 = size15;
	}

	public int getSize16() {
		return size16;
	}

	public void setSize16(int size16) {
		this.size16 = size16;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	@Override
	public String toString() {
		return "CreateOrderRequestItem [SeasonId=" + SeasonId + ", CollectionId=" + CollectionId + ", ProductId="
				+ ProductId + ", ColorId=" + ColorId + ", size01=" + size01 + ", size02=" + size02 + ", size03="
				+ size03 + ", size04=" + size04 + ", size05=" + size05 + ", size06=" + size06 + ", size07=" + size07
				+ ", size08=" + size08 + ", size09=" + size09 + ", size10=" + size10 + ", size11=" + size11
				+ ", size12=" + size12 + ", size13=" + size13 + ", size14=" + size14 + ", size15=" + size15
				+ ", size16=" + size16 + ", price=" + price + "]";
	}
    
}
