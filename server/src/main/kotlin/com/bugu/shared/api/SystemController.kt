package com.bugu.shared.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 底座自检接口:确认应用、统一响应与序列化链路可用。
 */
@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    @GetMapping("/ping")
    fun ping(): ApiResponse<String> = ApiResponse.ok("pong")
}
