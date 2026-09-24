document.addEventListener("alpine:init", function () {
  Alpine.data("greetingName", function () {
    return {
      length: 0,
      limit: 0,

      init: function () {
        var input = this.$el.querySelector("input");
        this.limit = Number(input.getAttribute("maxlength"));
        this.length = input.value.length;
      },

      update: function (event) {
        this.length = event.target.value.length;
      },

      get remaining() {
        return this.limit - this.length + " characters left";
      },
    };
  });
});
