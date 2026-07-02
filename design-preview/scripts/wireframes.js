document.querySelectorAll("[data-route]").forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.querySelector(button.dataset.route);
    if (!target) return;
    target.scrollIntoView({ behavior: "smooth", block: "start" });
    target.classList.add("focus-flash");
    window.setTimeout(() => target.classList.remove("focus-flash"), 900);
  });
});
