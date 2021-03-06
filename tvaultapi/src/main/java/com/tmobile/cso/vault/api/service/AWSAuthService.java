// =========================================================================
// Copyright 2018 T-Mobile, US
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// See the readme.txt file for additional language around disclaimer of warranties.
// =========================================================================

package com.tmobile.cso.vault.api.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmobile.cso.vault.api.controller.ControllerUtil;
import com.tmobile.cso.vault.api.model.AWSLogin;
import com.tmobile.cso.vault.api.model.AWSLoginRole;
import com.tmobile.cso.vault.api.process.RequestProcessor;
import com.tmobile.cso.vault.api.process.Response;
import com.tmobile.cso.vault.api.utils.JSONUtil;

@Component
public class  AWSAuthService {

	@Value("${vault.port}")
	private String vaultPort;

	@Autowired
	private RequestProcessor reqProcessor;

	@Value("${vault.auth.method}")
	private String vaultAuthMethod;

	private static Logger logger = LogManager.getLogger(AWSAuthService.class);
	/**
	 * To authenticate using aws ec2 pkcs7 document and app role
	 * @param login
	 * @return
	 */
	public ResponseEntity<String> authenticateLdap(AWSLogin login){
		String jsonStr = JSONUtil.getJSON(login);
		if(jsonStr.toLowerCase().contains("nonce")){
			return ResponseEntity.badRequest().body("{\"errors\":[\"Not a valid request. Parameter 'nonce' is not expected \"]}");
		}

		String nonce= "";
		try {
			nonce = new ObjectMapper().readTree(jsonStr).at("/pkcs7").toString().substring(1,50);
		} catch (IOException e) {
			// Log exception
			e.printStackTrace();
			return ResponseEntity.badRequest().body("{\"errors\":[\"Not valid request. Check params \"]}");
		}
		String noncejson = "{\"nonce\":\""+nonce+"\",";
		jsonStr = noncejson + jsonStr.substring(1);

		Response response = reqProcessor.process("/auth/aws/login",jsonStr,"");
		return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());
	}
	/**
	 * To create an aws app role
	 * @param token
	 * @param awsLoginRole
	 * @return
	 */
	public ResponseEntity<String> createRole(String token, AWSLoginRole awsLoginRole){
		String jsonStr = JSONUtil.getJSON(awsLoginRole);
		ObjectMapper objMapper = new ObjectMapper();
		String currentPolicies = "";
		String latestPolicies = "";
		String roleName = "" ;

		try {
			JsonNode root = objMapper.readTree(jsonStr);
			roleName = root.get("role").asText();
			if(root.get("policies") != null)
				latestPolicies = root.get("policies").asText();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Response response = reqProcessor.process("/auth/aws/roles/create",jsonStr,token);

		if(response.getHttpstatus().equals(HttpStatus.NO_CONTENT)){ // Role created with policies. Need to update SDB metadata too.
			response = ControllerUtil.updateMetaDataOnConfigChanges(roleName, "roles", currentPolicies, latestPolicies, token);
			if(!HttpStatus.OK.equals(response.getHttpstatus()))
				return ResponseEntity.status(response.getHttpstatus()).body("{\"messages\":[\"AWS Role configured\",\""+response.getResponse()+"\"]}");
		}else{
			return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());
		}
		return ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"AWS Role created \"]}");
	}
	/**
	 * Method to update an aws app role.
	 * @param token
	 * @param awsLoginRole
	 * @return
	 */
	public ResponseEntity<String> updateRole(String token, AWSLoginRole awsLoginRole){
		String jsonStr = JSONUtil.getJSON(awsLoginRole);
		ObjectMapper objMapper = new ObjectMapper();
		String currentPolicies = "";
		String latestPolicies = "";
		String roleName = "" ;

		try {
			JsonNode root = objMapper.readTree(jsonStr);
			roleName = root.get("role").asText();
			if(root.get("policies") != null)
				latestPolicies = root.get("policies").asText();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Response awsResponse = reqProcessor.process("/auth/aws/roles","{\"role\":\""+roleName+"\"}",token);
		String responseJson="";	

		if(HttpStatus.OK.equals(awsResponse.getHttpstatus())){
			responseJson = awsResponse.getResponse();	
			try {
				Map<String,Object> responseMap; 
				responseMap = objMapper.readValue(responseJson, new TypeReference<Map<String, Object>>(){});
				@SuppressWarnings("unchecked")
				List<String> policies  = (List<String>) responseMap.get("policies");
				currentPolicies = policies.stream().collect(Collectors.joining(",")).toString();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"messages\":[\"Update failed . AWS Role does not exist \"]}");
		}

		Response response = reqProcessor.process("/auth/aws/roles/delete",jsonStr,token);
		if(response.getHttpstatus().equals(HttpStatus.NO_CONTENT)){
			response = reqProcessor.process("/auth/aws/roles/update",jsonStr,token);
			if(response.getHttpstatus().equals(HttpStatus.NO_CONTENT)){
				response = ControllerUtil.updateMetaDataOnConfigChanges(roleName, "aws-roles", currentPolicies, latestPolicies, token);
				if(!HttpStatus.OK.equals(response.getHttpstatus()))
					return ResponseEntity.status(response.getHttpstatus()).body("{\"messages\":[\"AWS Role configured\",\""+response.getResponse()+"\"]}");
				return ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"AWS Role updated \"]}");
			}else{
				return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());
			}
		}else{
			return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());
		}
	}
	/**
	 * Method to delete an existing role.
	 * @param token
	 * @param role
	 * @return
	 */
	public ResponseEntity<String> deleteRole(String token, String role){
		Response response = reqProcessor.process("/auth/aws/roles/delete","{\"role\":\""+role+"\"}",token);
		if(response.getHttpstatus().equals(HttpStatus.NO_CONTENT)){
			return ResponseEntity.status(HttpStatus.OK).body("{\"messages\":[\"Role deleted \"]}");
		}else{
			return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());
		}
	}
	/**
	 * Method to fetch information for an aws approle.
	 * @param token
	 * @param role
	 * @return
	 */
	public ResponseEntity<String> fetchRole(String token, String role){
		String jsoninput= "{\"role\":\""+role+"\"}";
		Response response = reqProcessor.process("/auth/aws/roles",jsoninput,token);
		return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());	
	}
	/**
	 * To get list of AWS Roles
	 * @param token
	 * @return
	 */
	public ResponseEntity<String> listRoles(String token){
		Response response = reqProcessor.process("/auth/aws/roles/list","{}",token);
		return ResponseEntity.status(response.getHttpstatus()).body(response.getResponse());	
	}
}
