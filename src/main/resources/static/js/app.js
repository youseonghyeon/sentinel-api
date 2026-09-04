let menuPositionFrame;

function closeAllMenus(options = {}) {
    const activeButton = document.querySelector('.btn-more[aria-expanded="true"]');
    cancelAnimationFrame(menuPositionFrame);

    document.querySelectorAll('.menu.open').forEach((menu) => {
        menu.classList.remove('open');
        menu.style.removeProperty('top');
        menu.style.removeProperty('left');
    });
    document.querySelectorAll('.btn-more[aria-expanded="true"]').forEach((button) => {
        button.setAttribute('aria-expanded', 'false');
    });

    if (options.restoreFocus && activeButton) activeButton.focus();
}

function positionMenu(button, menu) {
    const buttonRect = button.getBoundingClientRect();
    const menuRect = menu.getBoundingClientRect();
    const viewportGap = 8;
    const menuGap = 6;
    const left = Math.max(
        viewportGap,
        Math.min(buttonRect.right - menuRect.width, window.innerWidth - menuRect.width - viewportGap),
    );
    const below = buttonRect.bottom + menuGap;
    const top = below + menuRect.height <= window.innerHeight - viewportGap
        ? below
        : Math.max(viewportGap, buttonRect.top - menuRect.height - menuGap);

    menu.style.left = `${left}px`;
    menu.style.top = `${top}px`;
}

function repositionOpenMenu() {
    const button = document.querySelector('.btn-more[aria-expanded="true"]');
    const menu = document.querySelector('.menu.open');
    if (!button || !menu) return;

    cancelAnimationFrame(menuPositionFrame);
    menuPositionFrame = requestAnimationFrame(() => positionMenu(button, menu));
}

function toggleMenu(button) {
    const menu = button.nextElementSibling;
    const wasOpen = menu.classList.contains('open');
    closeAllMenus();
    if (wasOpen) return;

    menu.classList.add('open');
    button.setAttribute('aria-expanded', 'true');
    positionMenu(button, menu);
}

window.closeAllMenus = closeAllMenus;
window.toggleMenu = toggleMenu;

document.addEventListener('DOMContentLoaded', () => {
    const sidebar = document.querySelector('.sidebar');
    const toggle = document.querySelector('.sidebar-toggle');

    if (!sidebar || !toggle) return;

    toggle.addEventListener('click', () => {
        const isOpen = sidebar.classList.toggle('is-open');
        toggle.setAttribute('aria-expanded', String(isOpen));
        toggle.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기');
    });

    window.addEventListener('resize', () => {
        closeAllMenus();
        if (window.innerWidth > 860 && sidebar.classList.contains('is-open')) {
            sidebar.classList.remove('is-open');
            toggle.setAttribute('aria-expanded', 'false');
            toggle.setAttribute('aria-label', '메뉴 열기');
        }
    });

    document.addEventListener('click', (event) => {
        if (!event.target.closest('.menu-wrap')) closeAllMenus();
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') closeAllMenus({ restoreFocus: true });
    });
    document.addEventListener('scroll', repositionOpenMenu, true);
});
