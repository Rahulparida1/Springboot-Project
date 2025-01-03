package com.jsp.integration.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsp.integration.dto.CustomerDto;
import com.jsp.integration.entity.Costumer;
import com.jsp.integration.repository.CustomerRepo;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Service
public class CustomerServiceImpl implements CustomerService {
	
	@Value("${twilio.account-sid}")
	private String ACCOUNT_SID;
	@Value("${twilio.auth-token}")
	private String AUTH_TOKEN;
	@Value("${twilio.phone-number}")
	private String FROM_NUMBER;

	@Autowired
	private CustomerRepo customerRepo;
	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	ObjectMapper mapper = new ObjectMapper();

	public Costumer customerDetails(CustomerDto customerDto) {
		
		Costumer customer = new Costumer();
		customer.setUserName(customerDto.getUserName());
		customer.setTeleNumber(customerDto.getTeleNumber());
		customer.setSmsStatus(customerDto.getSmsStatus());
		
		return customer;

	}
	
	@Override
	public Costumer saveCustomer(CustomerDto customerDto) {

		return  customerRepo.save(customerDetails(customerDto));
	}

	public Map<String, Object> getCustomer(long uniqueId) {
		Optional<Costumer> customer = customerRepo.findById(uniqueId);
		Costumer customer2 = customer.orElseThrow();
		@SuppressWarnings("unchecked")
		Map<String, Object> customerMap = mapper.convertValue(customer2, Map.class);
		return customerMap;
	}
	
	public String getCustomerUserName(long uniqueId) {
		String customerUserName=(String) getCustomer(uniqueId).get("userName");
		return customerUserName;
	}
	public String getCustomerTeleNum(long uniqueId) {
		String customerTeleNum=(String) getCustomer(uniqueId).get("teleNumber");
		return customerTeleNum;
	}
	public String createSmsBody(long uniqueId , String templateCode) throws JsonMappingException, JsonProcessingException {
		String url = "http://localhost:8081/getbyCode/"+templateCode;
		ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> template = mapper.readValue(exchange.getBody(), Map.class);
		String templateContent = (String) template.get("templateContent");
		
		byte[] decode = Base64.getDecoder().decode(templateContent.getBytes());
		String decodedTempletContent=new String(decode,StandardCharsets.UTF_8);  
		String templateSubject=(String) template.get("templateSubject");
		Object object = template.get("placeholder");
//		template.get("entityId");
		String message= templateSubject+ ":-" +decodedTempletContent;
		System.out.println(message);
		String msg="";
		if(object instanceof String ) {
			List<Map<String, String>> readValue = mapper.readValue((String)object,new TypeReference<List<Map<String, String>>>() {});
			for(Map<String, String> placeholders : readValue) {
		    	 String fieldName = placeholders.get("entityFieldName");
		    	 String placeholderKey = placeholders.get("placeholderKey");
		    	 
		    	 
		    	 if("user_name".equals(fieldName)) {
		    		 if(message.contains(placeholderKey)) {
		    			msg= message.replace(placeholderKey,getCustomerUserName(uniqueId) );
		    		 }
		    	 }
		    	 else if("tele_number".equals(fieldName)) {
		    		 if(message.contains(placeholderKey)) {
		    			msg = msg.replace(placeholderKey,getCustomerTeleNum(uniqueId) );
		    		 }
		    	 }
		     }  
		 } 
		
		 System.out.println(msg);
		return msg;
		
	}

	@Override
	public void sendRealTimeSms(long uniqueId, String templateCode)
			throws JsonMappingException, JsonProcessingException {
		Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
		
		
		String smsBody = createSmsBody(uniqueId, templateCode);
		System.out.println(smsBody);
	
	
	Optional<Costumer> findById = customerRepo.findById(uniqueId);
	Costumer customer = findById.get();
	
	Message create = Message.creator(new PhoneNumber(customer.getTeleNumber()), new PhoneNumber(FROM_NUMBER), smsBody).create();
	if ( create != null) {
         
        String updateQuery = "UPDATE customer SET sms_status = ? WHERE u_id = ?";
        jdbcTemplate.update(updateQuery, "success", uniqueId);
    }
	
  }

}


