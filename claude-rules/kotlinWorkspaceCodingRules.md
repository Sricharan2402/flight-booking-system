# Kotlin Language Rules

## Overview

You are an expert in **Kotlin programming** and related **JVM technologies**.

---

## Kotlin-Specific Best Practices

### Immutability and Safety
- **Prefer `val` over `var`** to create immutable references
- **Utilize Kotlin's null safety features** to prevent null pointer exceptions
- **Use data classes** for DTOs and immutable data structures
- **Prefer immutable collections** when possible

### Language Features
- **Leverage Kotlin's extension functions** to enhance existing classes without inheritance
- **Use sealed classes** for representing restricted class hierarchies
- **Implement Kotlin's scope functions** (`let`, `apply`, `run`, `with`, `also`) appropriately
- **Use destructuring declarations** where appropriate
- **Use sequence** for large collection processing
- **Utilize inline functions** for higher-order functions
- **Use reified type parameters** when needed
- **Leverage delegation pattern** with `by` keyword
- **Use companion objects** appropriately
- **Implement operator overloading** judiciously

### Dependency Injection
- **Use constructor-based dependency injection**

### Asynchronous Programming
- **Leverage Kotlin's coroutines** for asynchronous programming

---

## Naming Conventions

### Class and Interface Names
- **Use PascalCase** for class names (e.g., `UserController`, `OrderService`)
- **Prefix interface names with 'I'** only when there's a class with the same name

### Functions and Variables
- **Use camelCase** for method and variable names (e.g., `findUserById`, `isOrderValid`)
- **Use verb phrases** for function names (e.g., `calculateTotal`, `processPayment`)
- **Use noun phrases** for properties and variables (e.g., `userName`, `orderTotal`)

### Constants
- **Use ALL_CAPS** for constants (e.g., `MAX_RETRY_ATTEMPTS`, `DEFAULT_PAGE_SIZE`)

### General Guidelines
- **Use meaningful and descriptive names** that reflect purpose

---

## Code Structure

### Function Design
- **Keep functions small** and focused on a single responsibility
- **Limit function parameters** (consider using data classes for multiple parameters)
- **Use expression bodies** for simple functions

### Organization
- **Organize code with extensions** in separate files by receiver type
- **Group related properties and functions** together
- **Use proper package structure** to organize code

### Design Principles
- **Follow SOLID principles** to ensure code is maintainable and extensible

---

## Kotlin Coroutines

### Dispatcher Usage
- **Use the appropriate dispatcher** (IO for I/O operations, Default for CPU-intensive tasks)
- **Use `withContext`** for changing context without creating new coroutines

### Exception Handling
- **Properly handle exceptions** in coroutines with try-catch or supervisorScope
- **Use structured concurrency** with coroutineScope

### Lifecycle Management
- **Cancel coroutines** when no longer needed
- **Avoid using `runBlocking`** in production code

### Data Streams
- **Use Flow** for asynchronous streams of data
- **Apply backpressure** with buffer, conflate, or collectLatest when needed

### Parallel Execution
- **Use async/await** for parallel decomposition

---

## Kotlin Coroutines Testing

### Test Setup
- **Use `runTest`** for testing suspend functions in unit tests
- **Properly wrap assertions** that test suspending functions with runBlocking when needed
- **Ensure mock setup** is compatible with coroutine execution

### Mock Configuration
- **Always mock ALL method calls** that will occur during test execution, including chained calls
- **For each mocked method** that returns a value used later in the service, explicitly define behavior with `every { ... } returns ...`
- **Verify all important mock interactions** with `verify(exactly = n) { ... }`
