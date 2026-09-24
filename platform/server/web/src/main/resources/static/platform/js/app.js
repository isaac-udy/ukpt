// htmx does not swap a failed response; show its text in the layout's error region instead.
(function () {
  function showError(message) {
    var region = document.getElementById("app-error");
    if (!region) return;
    region.textContent = message;
    region.hidden = false;
  }

  function hideError() {
    var region = document.getElementById("app-error");
    if (region) region.hidden = true;
  }

  document.addEventListener("htmx:beforeRequest", hideError);

  document.addEventListener("htmx:responseError", function (event) {
    var text = event.detail.xhr.responseText;
    showError(text && text.length < 200 ? text : "The request failed.");
  });

  document.addEventListener("htmx:sendError", function () {
    showError("The server could not be reached.");
  });
})();
