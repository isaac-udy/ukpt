# Domain exceptions

* **Definition**: A class that represents a known failure mode raised by a domain interface.
* **Note**: Domain exceptions live at the top of the `domain` package when shared between multiple domain interfaces, or as a nested class on the [domain interface](#domain-interfaces) that throws them; they must be listed in `@Throws` on the throwing interface's primary function.
