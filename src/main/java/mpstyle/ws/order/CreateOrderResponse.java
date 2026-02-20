/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.ws.order;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author Nicola
 */
@XmlRootElement(name = "response")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateOrderResponse {
	
	private CreateOrderResponseOrder order;

    public CreateOrderResponseOrder getOrder() {
        return order;
    }

    public void setOrder(CreateOrderResponseOrder order) {
        this.order = order;
    }

	@Override
	public String toString() {
		return "CreateOrderResponse [order=" + order.toString() + "]";
	}
    
}
