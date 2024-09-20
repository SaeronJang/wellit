package com.wellit.project;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MainController {
	
	@GetMapping("/")
	public String index() {
		return "index";
	}

	// 로그인 오류 창 후 로그인 페이지로 리다이렉트
	@GetMapping("/login-alert")
	public String loginAlert() {
		return "/error/login_alert";
	}



}
