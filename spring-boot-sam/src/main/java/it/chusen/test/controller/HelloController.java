package it.chusen.test.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author chusen
 * @date 2020/7/16 5:18 下午
 */
@RestController
public class HelloController {

	@GetMapping("/hello")
	public String hello() {
		return "hello world";
	}
}
