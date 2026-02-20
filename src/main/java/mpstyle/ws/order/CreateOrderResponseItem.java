/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.ws.order;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "item")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateOrderResponseItem {
	
	
	@XmlElement(name = "season-id")
    private String SeasonId;

    @XmlElement(name = "collection-id")
    private String CollectionId;

    @XmlElement(name = "product-id")
    private String ProductId;

    @XmlElement(name = "color-id")
    private String ColorId;
    
    @XmlElement(name = "size")
    private String Size;
    
    @XmlElement(name = "qta")
    private int QtaOrd;
    
    @XmlElement(name = "order-row")
    private BigDecimal orderRow;

	/**
     * @return the SeasonId
     */
    public String getSeasonId() {
        return SeasonId;
    }

    /**
     * @param SeasonId the SeasonId to set
     */
    public void setSeasonId(String SeasonId) {
        this.SeasonId = SeasonId;
    }

    /**
     * @return the CollectionId
     */
    public String getCollectionId() {
        return CollectionId;
    }

    /**
     * @param CollectionId the CollectionId to set
     */
    public void setCollectionId(String CollectionId) {
        this.CollectionId = CollectionId;
    }

    /**
     * @return the ProductId
     */
    public String getProductId() {
        return ProductId;
    }

    /**
     * @param ProductId the ProductId to set
     */
    public void setProductId(String ProductId) {
        this.ProductId = ProductId;
    }

    /**
     * @return the ColorId
     */
    public String getColorId() {
        return ColorId;
    }

    /**
     * @param ColorId the ColorId to set
     */
    public void setColorId(String ColorId) {
        this.ColorId = ColorId;
    }
    
    /**
     * @return the Size
     */
    public String getSize() {
		return Size;
	}

    /**
     * @param Size the Size to set
     */
	public void setSize(String size) {
		Size = size;
	}
	
	/**
     * @return the QtaOrd
     */

	public int getQtaOrd() {
		return QtaOrd;
	}

	/**
     * @param QtaOrd the QtaOrd to set
     */
	
	public void setQtaOrd(int qtaOrd) {
		QtaOrd = qtaOrd;
	}

	public BigDecimal getOrderRow() {
		return orderRow;
	}

	public void setOrderRow(BigDecimal orderRow) {
		this.orderRow = orderRow;
	}

	@Override
	public String toString() {
		return "CreateOrderResponseItem [SeasonId=" + SeasonId + ", CollectionId=" + CollectionId + ", ProductId="
				+ ProductId + ", ColorId=" + ColorId + ", Size=" + Size + ", QtaOrd=" + QtaOrd + ", orderRow="
				+ orderRow + "]";
	}

}
