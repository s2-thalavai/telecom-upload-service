// KYC desk: talks to the upload API. Uses XMLHttpRequest for uploads to show real progress.
const API = '/api/v1/customers';
const $ = (s) => document.querySelector(s);
let selectedFile = null;

async function loadCustomers() {
  const select = $('#customer');
  try {
    const res = await fetch(API);
    const customers = await res.json();
    select.innerHTML = customers.length
      ? customers.map((c) => `<option value="${c.id}">${esc(c.name)} · ${esc(c.msisdn)}</option>`).join('')
      : '<option value="">No customers yet – import a CSV below</option>';
    select.dataset.customers = JSON.stringify(customers);
    onCustomerChange();
  } catch {
    select.innerHTML = '<option value="">Could not load customers</option>';
  }
}

function onCustomerChange() {
  const id = $('#customer').value;
  const customers = JSON.parse($('#customer').dataset.customers || '[]');
  const c = customers.find((x) => String(x.id) === id);
  $('#customer-meta').textContent = c ? `${c.email} · ${c.planType.toLowerCase()} plan` : '';
  refreshUploadButton();
  loadDocuments();
}

async function loadDocuments() {
  const id = $('#customer').value;
  const body = $('#docs');
  if (!id) { body.innerHTML = '<tr><td colspan="5" class="muted">Choose a customer to see their documents.</td></tr>'; return; }
  const res = await fetch(`${API}/${id}/documents`);
  const docs = res.ok ? await res.json() : [];
  body.innerHTML = docs.length ? docs.map((d) => `
      <tr>
        <td>${label(d.type)}</td>
        <td><a href="${d.downloadUrl}">${esc(d.originalFilename)}</a>${d.description ? `<br><span class="muted">${esc(d.description)}</span>` : ''}</td>
        <td class="num">${size(d.sizeBytes)}</td>
        <td class="num">${new Date(d.uploadedAt).toLocaleString()}</td>
        <td><button class="link" data-delete="${d.downloadUrl}" type="button">Delete</button></td>
      </tr>`).join('')
    : '<tr><td colspan="5" class="muted">No documents on file. Add one on the left.</td></tr>';
}

function chooseFile(file) {
  selectedFile = file || null;
  $('#drop-text').textContent = file ? `${file.name} (${size(file.size)})` : 'Drop a PDF, PNG or JPEG here, or choose a file';
  refreshUploadButton();
}

function refreshUploadButton() {
  $('#upload').disabled = !(selectedFile && $('#customer').value);
}

function upload() {
  const id = $('#customer').value;
  const mode = document.querySelector('input[name="mode"]:checked').value;
  const type = $('#doc-type').value;
  const xhr = new XMLHttpRequest();
  let body;

  if (mode === 'multipart') {
    body = new FormData();
    body.append('file', selectedFile);
    body.append('type', type);
    const description = $('#description').value.trim();
    if (description) body.append('description', description);
    xhr.open('POST', `${API}/${id}/documents`);
  } else {
    body = selectedFile;
    xhr.open('POST', `${API}/${id}/documents/stream?type=${encodeURIComponent(type)}`);
    xhr.setRequestHeader('Content-Type', selectedFile.type || 'application/octet-stream');
    xhr.setRequestHeader('X-Filename', selectedFile.name);
  }

  const progress = $('#progress');
  progress.hidden = false; progress.value = 0;
  status('Uploading…', '');
  $('#upload').disabled = true;

  xhr.upload.onprogress = (e) => { if (e.lengthComputable) progress.value = Math.round((e.loaded / e.total) * 100); };
  xhr.onload = () => {
    progress.hidden = true;
    refreshUploadButton();
    if (xhr.status === 201) {
      status('Uploaded. The document is on file.', 'ok');
      chooseFile(null); $('#file').value = ''; $('#description').value = '';
      loadDocuments();
    } else {
      status(problemMessage(xhr.status, xhr.responseText), xhr.status >= 500 ? 'err' : 'warn');
    }
  };
  xhr.onerror = () => { progress.hidden = true; refreshUploadButton(); status('The upload did not reach the server. Check your connection and try again.', 'err'); };
  xhr.send(body);
}

async function importCsv() {
  const file = $('#csv').files[0];
  const out = $('#import-result');
  if (!file) { out.innerHTML = '<p class="status warn">Choose a CSV file first.</p>'; return; }
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${API}/import`, { method: 'POST', body: form });
  const text = await res.text();
  if (!res.ok) { out.innerHTML = `<p class="status warn">${esc(problemMessage(res.status, text))}</p>`; return; }
  const r = JSON.parse(text);
  out.innerHTML = `<p class="status ${r.failed ? 'warn' : 'ok'}">Imported ${r.imported} of ${r.totalRows} rows${r.failed ? `; ${r.failed} rejected` : ''}.</p>`
    + (r.errors.length ? `<ul>${r.errors.map((e) => `<li>Line ${e.line}: ${esc(e.message)}</li>`).join('')}</ul>` : '');
  loadCustomers();
}

function problemMessage(status, text) {
  try {
    const p = JSON.parse(text);
    if (p.errors) return `${p.detail}: ${Object.entries(p.errors).map(([k, v]) => `${k} ${v}`).join(', ')}`;
    if (p.detail) return p.detail;
  } catch { /* not JSON */ }
  if (status === 413) return 'The file is larger than the allowed size. Try the raw stream option for big files.';
  return `Request failed with status ${status}.`;
}

function status(msg, kind) { const el = $('#upload-status'); el.textContent = msg; el.className = `status ${kind}`; }
function size(b) { return b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(1)} KB` : `${(b / 1048576).toFixed(1)} MB`; }
function label(t) { return t.charAt(0) + t.slice(1).toLowerCase().replace('_', ' '); }
function esc(s) { return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

// ---- wiring ----
$('#customer').addEventListener('change', onCustomerChange);
$('#file').addEventListener('change', (e) => chooseFile(e.target.files[0]));
$('#upload').addEventListener('click', upload);
$('#import').addEventListener('click', importCsv);
$('#docs').addEventListener('click', async (e) => {
  const url = e.target.dataset.delete;
  if (!url || !confirm('Delete this document? The file is removed permanently.')) return;
  const res = await fetch(url, { method: 'DELETE' });
  if (res.ok) loadDocuments(); else alert(problemMessage(res.status, await res.text()));
});
const zone = $('#drop-zone');
['dragenter', 'dragover'].forEach((ev) => zone.addEventListener(ev, (e) => { e.preventDefault(); zone.classList.add('dragging'); }));
['dragleave', 'drop'].forEach((ev) => zone.addEventListener(ev, (e) => { e.preventDefault(); zone.classList.remove('dragging'); }));
zone.addEventListener('drop', (e) => chooseFile(e.dataTransfer.files[0]));

loadCustomers();
