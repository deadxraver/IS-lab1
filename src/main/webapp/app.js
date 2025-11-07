(() => {
	const tableBody = document.querySelector('#routesTable tbody');
	const searchInput = document.getElementById('searchInput');
	const searchBtn = document.getElementById('searchBtn');
	const clearSearchBtn = document.getElementById('clearSearchBtn');
	const createBtn = document.getElementById('createBtn');
	const prevPage = document.getElementById('prevPage');
	const nextPage = document.getElementById('nextPage');
	const pageInfo = document.getElementById('pageInfo');
	const pageSizeSelect = document.getElementById('pageSize');

	const modal = document.getElementById('modal');
	const modalTitle = document.getElementById('modalTitle');
	const routeForm = document.getElementById('routeForm');
	const cancelBtn = document.getElementById('cancelBtn');
	const formError = document.getElementById('formError');

	let page = 0;
	let size = parseInt(pageSizeSelect.value, 10);
	let currentFilter = '';

	function apiUrl(path) {
		return ('http://localhost:25501/IS-lab1/api' + path);
	}

	async function loadRoutes() {
		try {
			let url;
			if (currentFilter && currentFilter.length > 0) {
				url = apiUrl(`/routes?name=${encodeURIComponent(currentFilter)}&page=${page}&size=${size}`);
			} else {
				url = apiUrl(`/routes?page=${page}&size=${size}`);
			}
			const resp = await fetch(url);
			if (!resp.ok) throw new Error('Ошибка загрузки: ' + resp.status);
			const data = await resp.json();

			let list = [];
			if (Array.isArray(data)) {
				list = data;
			} else if (data && Array.isArray(data.content)) {
				list = data.content;
			} else {
				// fallback: если пришёл объект с неизвестной структурой — попытаться получить any array properties
				for (const k of Object.keys(data || {})) {
					if (Array.isArray(data[k])) { list = data[k]; break; }
				}
			}

			// Если сервер не поддерживает фильтрацию (вернул все записи), и есть currentFilter — применим клиентский фильтр
			if (currentFilter && currentFilter.length > 0) {
				const lowered = currentFilter.toLowerCase();
				list = list.filter(r => (r.name || '').toLowerCase().includes(lowered));
			}

			renderTable(list);
		} catch (e) {
			console.error(e);
			tableBody.innerHTML = `<tr><td colspan="9">Ошибка загрузки данных</td></tr>`;
		}
	}

	function renderTable(list) {
		tableBody.innerHTML = '';
		if (!Array.isArray(list) || list.length === 0) {
			tableBody.innerHTML = '<tr><td colspan="9">Нет данных</td></tr>';
			pageInfo.textContent = `Страница ${page + 1}`;
			return;
		}
		for (const r of list) {
			const tr = document.createElement('tr');
			tr.innerHTML = `
        <td>${r.id ?? ''}</td>
        <td>${escapeHtml(r.name)}</td>
        <td>${r.coordinates ? `${r.coordinates.x}, ${r.coordinates.y}` : ''}</td>
        <td>${r.from ? escapeHtml(r.from.name) : ''}</td>
        <td>${r.to ? escapeHtml(r.to.name) : ''}</td>
        <td>${r.distance ?? ''}</td>
        <td>${r.rating ?? ''}</td>
        <td>${formatDate(r.creationDate)}</td>
        <td class="actions">
          <button data-id="${r.id}" class="editBtn">Ред.</button>
          <button data-id="${r.id}" class="delBtn">Удал.</button>
        </td>`;
			tableBody.appendChild(tr);
		}
		pageInfo.textContent = `Страница ${page + 1}`;
		attachRowHandlers();
	}

	function attachRowHandlers() {
		document.querySelectorAll('.editBtn').forEach(b => {
			b.addEventListener('click', () => openEdit(b.dataset.id));
		});
		document.querySelectorAll('.delBtn').forEach(b => {
			b.addEventListener('click', () => doDelete(b.dataset.id));
		});
	}

	async function doDelete(id) {
		if (!confirm('Удалить маршрут #' + id + '?')) return;
		try {
			const resp = await fetch(apiUrl(`/routes/${id}`), {method: 'DELETE'});
			if (!resp.ok) throw new Error('Удаление не удалось: ' + resp.status);
			await reloadAndStay();
		} catch (e) {
			alert('Ошибка удаления: ' + e.message);
		}
	}

	async function openEdit(id) {
		try {
			const resp = await fetch(apiUrl(`/routes/${id}`));
			if (!resp.ok) throw new Error('Не найден маршрут');
			const r = await resp.json();
			fillForm(r);
			modalTitle.textContent = 'Редактировать маршрут #' + id;
			showModal();
		} catch (e) {
			alert('Ошибка: ' + e.message);
		}
	}

	function fillForm(r) {
		document.getElementById('routeId').value = r.id ?? '';
		document.getElementById('name').value = r.name ?? '';
		document.getElementById('coordX').value = r.coordinates?.x ?? '';
		document.getElementById('coordY').value = r.coordinates?.y ?? '';
		document.getElementById('fromName').value = r.from?.name ?? '';
		document.getElementById('fromX').value = r.from?.x ?? '';
		document.getElementById('fromY').value = r.from?.y ?? '';
		document.getElementById('toName').value = r.to?.name ?? '';
		document.getElementById('toX').value = r.to?.x ?? '';
		document.getElementById('toY').value = r.to?.y ?? '';
		document.getElementById('distance').value = r.distance ?? '';
		document.getElementById('rating').value = r.rating ?? '';
		formError.textContent = '';
	}

	function showModal() {
		modal.classList.remove('hidden');
	}

	function hideModal() {
		modal.classList.add('hidden');
	}

	routeForm.addEventListener('submit', async (e) => {
		e.preventDefault();
		formError.textContent = '';
		const payload = collectForm();
		if (!payload) return;
		const id = document.getElementById('routeId').value;
		try {
			const method = id ? 'PUT' : 'POST';
			const url = id ? apiUrl(`/routes/${id}`) : apiUrl('/routes');
			const resp = await fetch(url, {
				method,
				headers: {'Content-Type': 'application/json'},
				body: JSON.stringify(payload)
			});
			if (!resp.ok) {
				const txt = await resp.text().catch(() => null);
				throw new Error(`${resp.status} ${txt || resp.statusText}`);
			}
			hideModal();
			await reloadAndStay();
		} catch (err) {
			formError.textContent = 'Ошибка сохранения: ' + err.message;
		}
	});

	function collectForm() {
		const name = document.getElementById('name').value.trim();
		if (!name) {
			formError.textContent = 'Название обязательно';
			return null;
		}
		const coordX = parseFloat(document.getElementById('coordX').value);
		const coordY = parseFloat(document.getElementById('coordY').value);
		const fromName = document.getElementById('fromName').value.trim();
		const fromX = parseInt(document.getElementById('fromX').value, 10);
		const fromY = parseInt(document.getElementById('fromY').value, 10);
		const distance = parseInt(document.getElementById('distance').value, 10);
		const rating = parseInt(document.getElementById('rating').value, 10);
		if (isNaN(distance) || distance < 2) {
			formError.textContent = 'Distance must be >= 2';
			return null;
		}
		if (isNaN(rating) || rating < 1) {
			formError.textContent = 'Rating must be >= 1';
			return null;
		}
		const payload = {
			name,
			coordinates: {x: coordX, y: coordY},
			from: {name: fromName, x: fromX, y: fromY},
			distance,
			rating
		};
		const toName = document.getElementById('toName').value.trim();
		const toX = document.getElementById('toX').value;
		const toY = document.getElementById('toY').value;
		if (toName || toX || toY) {
			payload.to = {name: toName || null};
			if (toX) payload.to.x = parseInt(toX, 10);
			if (toY) payload.to.y = toY ? parseInt(toY, 10) : null;
		}
		return payload;
	}

	cancelBtn.addEventListener('click', () => hideModal());
	createBtn.addEventListener('click', () => {
		routeForm.reset();
		document.getElementById('routeId').value = '';
		modalTitle.textContent = 'Создать маршрут';
		formError.textContent = '';
		showModal();
	});

	searchBtn.addEventListener('click', async () => {
		currentFilter = searchInput.value.trim();
		await searchAndShow();
	});
	clearSearchBtn.addEventListener('click', async () => {
		searchInput.value = '';
		currentFilter = '';
		page = 0;
		await reloadAndStay();
	});

	prevPage.addEventListener('click', async () => {
		if (page > 0) {
			page--;
			await reloadAndStay();
		}
	});
	nextPage.addEventListener('click', async () => {
		page++;
		await reloadAndStay();
	});
	pageSizeSelect.addEventListener('change', async () => {
		size = parseInt(pageSizeSelect.value, 10);
		page = 0;
		await reloadAndStay();
	});

	async function searchAndShow() {
		// при поиске сбрасываем страницу и вызываем общую загрузку, которая учтёт currentFilter
		if (!currentFilter) {
			page = 0;
			await loadRoutes();
			return;
		}
		page = 0;
		await loadRoutes();
	}

	setInterval(async () => {
		await loadRoutes();
	}, 5000);

	function escapeHtml(s) {
		if (s == null) return '';
		return String(s).replace(/[&<>"']/g, c => ({
			'&': '&amp;',
			'<': '&lt;',
			'>': '&gt;',
			'"': '&quot;',
			"'": '&#39;'
		})[c]);
	}

	// добавляем функцию форматирования/парсинга даты, устойчивую к формату ZonedDateTime с зоной в квадратных скобках и наносекундами
    function formatDate(s) {
        if (!s) return '';
        try {
            // убрать окончание вида [Europe/Moscow] или любой другой в скобках
            let t = s.replace(/\[.*\]$/, '').trim();
            // заменить дробную часть секунд (1..9 цифр) на миллисекунды (3 цифры, с доп. нулями или усечением)
            t = t.replace(/\.([0-9]{1,9})(?=[+-Z]|$)/, function(_, frac) {
                const ms = (frac + '000').slice(0, 3); // взять первые 3 цифры, дополнив нулями при необходимости
                return '.' + ms;
            });
            const d = new Date(t);
            return isNaN(d.getTime()) ? '' : d.toLocaleString();
        } catch (e) {
            return '';
        }
    }

	loadRoutes();
})();
