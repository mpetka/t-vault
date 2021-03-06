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

package com.tmobile.cso.vault.api.model;

public enum AccessPolicyEnum {
	READ("read", "r_"),
	WRITE("write", "w_"), 
	DENY ("deny", "d_");
	
	private String policyShort;
	private String policyLong;
	
	AccessPolicyEnum(String policyLong, String policyShort) {
		this.policyShort = policyShort;
		this.policyLong = policyLong;
	}
	
	public String policyShort() {
		return policyShort;
	}
	
	public String policyLong() {
		return policyLong;
	}
}
