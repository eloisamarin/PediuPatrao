// Alternar sidebar em telas pequenas
document.querySelector('.toggle-btn').addEventListener('click', () => {
    document.getElementById('sidebar').classList.toggle('active');
});

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

// ===== Desconto do gerente (fragments/desconto.html) =====
// Atalho 5/10/15/20%: preenche o campo do mesmo formulário
function preencherDesconto(botao) {
    const campo = botao.form.querySelector('input[name="percentual"]');
    campo.value = botao.dataset.pct;
    previaDesconto(campo);
    campo.focus();
}

// Prévia do novo valor (apenas exibição: o servidor recalcula e valida o limite de 20%)
function previaDesconto(campo) {
    const form = campo.form;
    const previa = form.querySelector('.previa-desconto');
    const bruto = parseFloat(form.dataset.bruto || '0');
    const pct = parseFloat(String(campo.value).replace(',', '.'));
    const moeda = v => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    if (isNaN(pct) || pct <= 0) {
        previa.textContent = 'Máximo de 20% do valor do pedido.';
        previa.className = 'form-text previa-desconto';
    } else if (pct > 20) {
        previa.textContent = 'Desconto acima de 20% não é permitido.';
        previa.className = 'form-text previa-desconto text-danger fw-medium';
    } else {
        previa.textContent = 'Novo valor: ' + moeda(bruto * (100 - pct) / 100) + ' (desconto de ' + moeda(bruto * pct / 100) + ')';
        previa.className = 'form-text previa-desconto text-success';
    }
}