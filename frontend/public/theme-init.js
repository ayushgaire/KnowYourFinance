// Set the saved or system theme before the first paint so there is no light flash.
// index.html loads this file before anything is drawn. Keep the storage key and
// values in step with src/utils/theme.js.
try {
  var t = localStorage.getItem("kyf-theme");
  if (t !== "light" && t !== "dark") t = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  document.documentElement.setAttribute("data-theme", t);
} catch {
  // Storage can be blocked; the page then keeps the default look.
}
