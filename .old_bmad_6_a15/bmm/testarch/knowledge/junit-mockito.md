# JUnit + Mockito Essentials

Use JUnit 5 (Jupiter) with Mockito for unit and lightweight integration tests. Keep tests deterministic, isolated, and fast.

## Core Patterns

- Use `@BeforeEach` and `@AfterEach` for setup/teardown.
- Prefer one assertion per test when possible.
- Use descriptive test names (Given-When-Then).
- Avoid shared mutable state across tests.
- Use `@Nested` to group related behaviors.

## Mockito Basics

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExampleServiceTest {
  @Mock
  private ExampleRepository repository;

  @InjectMocks
  private ExampleService service;

  @Test
  void shouldReturnValueFromRepository() {
    // GIVEN
    when(repository.fetch()).thenReturn("value");

    // WHEN
    String result = service.getValue();

    // THEN
    assertThat(result).isEqualTo("value");
  }
}
```

## Parameterized Tests

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HostValidatorTest {
  @ParameterizedTest
  @ValueSource(strings = {"localhost", "127.0.0.1", "::1"})
  void shouldAllowLoopbackHosts(String host) {
    assertThat(HostValidator.isAllowed(host)).isTrue();
  }
}
```

## Test Data Builders

Use factories or builders for test data and keep them in `src/test/java/.../factories`:

```java
class UserFactory {
  User createUser() {
    return new User("user@example.com", "User Name");
  }
}
```

## Common Pitfalls

- Avoid `Thread.sleep` in tests.
- Prefer explicit assertions over indirect state checks.
- Ensure mocks are scoped to a single test class.
