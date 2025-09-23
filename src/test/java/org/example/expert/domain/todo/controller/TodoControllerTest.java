package org.example.expert.domain.todo.controller;

import org.example.expert.config.JwtAuthenticationFilter;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.todo.dto.response.TodoResponse;
import org.example.expert.domain.todo.service.TodoService;
import org.example.expert.domain.user.dto.response.UserResponse;
import org.example.expert.domain.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoController.class)
// Spring Security 필터 미적용
// -> JWT 인증 없이 단위 테스트
@AutoConfigureMockMvc(addFilters = false)
class TodoControllerTest {

    // 테스트용 추가 설정 빈 정의
    @TestConfiguration
    static class TestConfig {

        // 실제 JWT 필터 대신 Mock 주입
        @MockBean
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        // 컨트롤러 메서드에서 테스트용 Mock AuthUser 객체 주입
        @Bean
        public HandlerMethodArgumentResolver authUserArgumentResolver() {
            return new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType().equals(AuthUser.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter,
                                              ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest,
                                              WebDataBinderFactory binderFactory) {
                    return new AuthUser(1L, "test@email.com", UserRole.ROLE_USER);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    // 테스트 후 Spring Security 초기화
    // 이전 테스트 인증 정보가 남아있으면, 다른 테스트에 영향을 줄 수 있기 때문
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void todo_단건_조회에_성공한다() throws Exception {
        // given
        long todoId = 1L;
        String title = "title";
        TodoResponse response = new TodoResponse(
                todoId,
                title,
                "contents",
                "Sunny",
                new UserResponse(1L, "test@email.com"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        // when
        when(todoService.getTodo(todoId)).thenReturn(response);

        // then
        mockMvc.perform(get("/todos/{todoId}", todoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todoId))
                .andExpect(jsonPath("$.title").value(title));
    }

    @Test
    void todo_단건_조회_시_todo가_존재하지_않아_예외가_발생한다() throws Exception {
        // given
        long todoId = 1L;

        // when
        when(todoService.getTodo(todoId))
                .thenThrow(new InvalidRequestException("Todo not found"));

        // then
        mockMvc.perform(get("/todos/{todoId}", todoId))
                // 실패 시 GlobalExceptionHandler의 invalidRequestExceptionException를 통해 BAD_REQUEST 반환해야함
                // 200 반환 코드로 인해 오류 발생
                // .andExpect(status().isOk())
                // .andExpect(jsonPath("$.status").value(HttpStatus.OK.name()))
                // .andExpect(jsonPath("$.code").value(HttpStatus.OK.value()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Todo not found"));
    }
}
