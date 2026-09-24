A Form holds what was submitted; `validate()` produces the value the domain takes, or the errors
the re-rendered form shows:

```kotlin
internal data class OrderForm(
    val item: String,
    val quantity: String,
) {
    fun validate(): FormResult<NewOrder, Errors> {
        val quantity = quantity.trim().toIntOrNull()
        val errors = Errors(
            item = if (item.isBlank()) "Choose an item." else null,
            quantity = if (quantity == null || quantity < 1) "Enter a quantity of at least 1." else null,
        )
        return if (errors.item == null && errors.quantity == null) {
            FormResult.Valid(NewOrder(item = item.trim(), quantity = checkNotNull(quantity)))
        } else {
            FormResult.Invalid(errors)
        }
    }

    data class Errors(val item: String?, val quantity: String?)

    companion object {
        val Empty = OrderForm(item = "", quantity = "1")

        fun from(parameters: Parameters): OrderForm =
            OrderForm(item = parameters["item"].orEmpty(), quantity = parameters["quantity"].orEmpty())
    }
}
```
