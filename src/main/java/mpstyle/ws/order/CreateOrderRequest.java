package mpstyle.ws.order;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "request")
@XmlAccessorType(XmlAccessType.FIELD)
public class CreateOrderRequest 
{
	private CreateOrderRequestOrder order;

	public CreateOrderRequestOrder getOrder() {
		return order;
	}

	public void setOrder(CreateOrderRequestOrder order) {
		this.order = order;
	}

	@Override
	public String toString() {
		return "CreateOrderRequest [order=" + order + "]";
	}
	
}
