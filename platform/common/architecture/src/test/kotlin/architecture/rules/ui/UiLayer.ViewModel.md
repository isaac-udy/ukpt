# ViewModels

A class that manages the UI state for a Screen and orchestrates calls to domain interfaces to load data and perform side effects based on user actions.
* **Note**: The `navigation` handle is used to read Destination parameters and perform navigation. When closing/completing a screen, use `NavigationHandle.close` when the user is cancelling or backing out, and `NavigationHandle.complete` when the user has successfully performed an action.
