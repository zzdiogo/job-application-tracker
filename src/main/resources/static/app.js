'use strict';

const STATUSES = ['WISHLIST', 'APPLIED', 'INTERVIEW', 'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'];
const STAGES = ['PHONE_SCREEN', 'TECHNICAL', 'HR', 'ON_SITE', 'FINAL', 'OTHER'];
const OUTCOMES = ['PENDING', 'PASSED', 'FAILED'];

const $ = (id) => document.getElementById(id);
const label = (value) => value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ');
const formatTime = (iso) => (iso ? new Date(iso).toLocaleString() : 'No date');

let currentId = null;
let registering = false;

// All page text goes through textContent, so stored data can never run as code.
function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function fillSelect(select, values) {
  values.forEach((value) => {
    const option = element('option', label(value));
    option.value = value;
    select.append(option);
  });
}

function showError(message) {
  $('error').textContent = message || '';
  $('error').hidden = !message;
}

const getToken = () => sessionStorage.getItem('token');

async function api(path, options = {}) {
  const headers = { ...options.headers };
  if (options.body) headers['Content-Type'] = 'application/json';
  if (getToken()) headers.Authorization = `Bearer ${getToken()}`;

  const response = await fetch(`/api${path}`, {
    ...options,
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (response.status === 401 && getToken()) {
    sessionStorage.clear();
    render();
    throw new Error('Your session has expired. Please log in again.');
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Request failed (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

/** Runs an action and shows any failure instead of leaving the user guessing. */
const guarded = (action) => async (event) => {
  event?.preventDefault();
  showError('');
  try {
    await action(event);
  } catch (error) {
    showError(error.message);
  }
};

function formValues(form) {
  const values = {};
  new FormData(form).forEach((value, key) => {
    values[key] = value.trim() === '' ? null : value;
  });
  return values;
}

function render() {
  const loggedIn = Boolean(getToken());
  $('auth').hidden = loggedIn;
  $('board').hidden = !loggedIn;
  $('session').hidden = !loggedIn;
  if (!loggedIn) $('detail').hidden = true;
  $('current-email').textContent = sessionStorage.getItem('email') || '';
  if (loggedIn) guarded(loadApplications)();
}

function setAuthMode(isRegistering) {
  registering = isRegistering;
  const title = isRegistering ? 'Create an account' : 'Log in';
  $('auth-title').textContent = title;
  $('auth-submit').textContent = isRegistering ? 'Register' : 'Log in';
  $('auth-toggle').textContent = isRegistering ? 'Already have an account? Log in' : 'Need an account? Register';
}

async function loadApplications() {
  const status = $('status-filter').value;
  const query = status ? `?status=${encodeURIComponent(status)}&size=100` : '?size=100';
  const page = await api(`/applications${query}`);
  const rows = page.items.map((application) => {
    const row = element('tr');
    row.tabIndex = 0;
    const cell = (text) => row.append(element('td', text));
    cell(application.company);
    cell(application.role);
    const statusCell = element('td');
    statusCell.append(element('span', label(application.status), 'badge'));
    row.append(statusCell);
    cell(application.appliedOn || '');
    cell(application.location || '');
    const open = guarded(() => openDetail(application.id));
    row.addEventListener('click', open);
    row.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') open(event);
    });
    return row;
  });
  $('applications').replaceChildren(...rows);
  $('empty').hidden = page.items.length > 0;
}

function openNew() {
  currentId = null;
  $('application-form').reset();
  $('detail-title').textContent = 'New application';
  $('children').hidden = true;
  $('delete-application').hidden = true;
  $('job-link').hidden = true;
  $('detail').hidden = false;
  $('application-form').elements.company.focus();
}

async function openDetail(id) {
  const application = await api(`/applications/${id}`);
  currentId = id;
  const form = $('application-form');
  ['company', 'role', 'url', 'location', 'status', 'appliedOn', 'notes'].forEach((field) => {
    form.elements[field].value = application[field] ?? '';
  });
  $('detail-title').textContent = `${application.role} at ${application.company}`;
  $('job-link').hidden = !application.url;
  if (application.url) $('job-link').href = application.url;
  $('delete-application').hidden = false;
  $('children').hidden = false;
  $('detail').hidden = false;
  await loadChildren();
  $('detail').scrollIntoView({ behavior: 'smooth' });
}

function deleteButton(path) {
  const button = element('button', 'Remove', 'secondary');
  button.type = 'button';
  button.addEventListener('click', guarded(async () => {
    await api(path, { method: 'DELETE' });
    await loadChildren();
  }));
  return button;
}

async function loadChildren() {
  const base = `/applications/${currentId}`;
  const [interviews, reminders, history] = await Promise.all([
    api(`${base}/interviews`),
    api(`${base}/reminders`),
    api(`${base}/history`),
  ]);

  $('interviews').replaceChildren(...interviews.map((interview) => {
    const item = element('li');
    const notes = interview.notes ? ` - ${interview.notes}` : '';
    item.append(element('span', `${label(interview.stage)}, ${formatTime(interview.scheduledAt)}${notes}`));
    const outcome = element('select');
    outcome.setAttribute('aria-label', 'Outcome');
    outcome.style.width = 'auto';
    fillSelect(outcome, OUTCOMES);
    outcome.value = interview.outcome;
    outcome.addEventListener('change', guarded(() => api(`${base}/interviews/${interview.id}`, {
      method: 'PUT',
      body: { ...interview, outcome: outcome.value },
    })));
    const controls = element('span');
    controls.style.display = 'flex';
    controls.style.gap = '6px';
    controls.append(outcome, deleteButton(`${base}/interviews/${interview.id}`));
    item.append(controls);
    return item;
  }));

  $('reminders').replaceChildren(...reminders.map((reminder) => {
    const item = element('li');
    const state = reminder.sentAt ? 'emailed' : (new Date(reminder.remindAt) <= new Date() ? 'due now' : 'scheduled');
    item.append(element('span', `${formatTime(reminder.remindAt)} (${state}): ${reminder.message}`));
    item.append(deleteButton(`${base}/reminders/${reminder.id}`));
    return item;
  }));

  $('history').replaceChildren(...history.map((change) => element(
    'li',
    `${formatTime(change.changedAt)}: ${change.from ? `${label(change.from)} to ` : 'Created as '}${label(change.to)}`,
  )));
}

function init() {
  fillSelect($('status-filter'), STATUSES);
  fillSelect($('application-form').elements.status, STATUSES);
  fillSelect($('interview-form').elements.stage, STAGES);

  $('auth-form').addEventListener('submit', guarded(async (event) => {
    const { email, password } = formValues(event.target);
    const path = registering ? '/auth/register' : '/auth/login';
    const session = await api(path, { method: 'POST', body: { email, password } });
    sessionStorage.setItem('token', session.token);
    sessionStorage.setItem('email', session.email);
    event.target.reset();
    setAuthMode(false); // The next time this form appears, it should be for logging in.
    render();
  }));
  $('auth-toggle').addEventListener('click', () => setAuthMode(!registering));
  $('logout').addEventListener('click', () => {
    sessionStorage.clear();
    render();
  });

  $('status-filter').addEventListener('change', guarded(loadApplications));
  $('new-application').addEventListener('click', openNew);
  $('close-detail').addEventListener('click', () => {
    $('detail').hidden = true;
  });

  $('application-form').addEventListener('submit', guarded(async (event) => {
    const body = formValues(event.target);
    const saved = currentId
      ? await api(`/applications/${currentId}`, { method: 'PUT', body })
      : await api('/applications', { method: 'POST', body });
    await loadApplications();
    await openDetail(saved.id);
  }));

  $('delete-application').addEventListener('click', guarded(async () => {
    await api(`/applications/${currentId}`, { method: 'DELETE' });
    $('detail').hidden = true;
    await loadApplications();
  }));

  $('interview-form').addEventListener('submit', guarded(async (event) => {
    const values = formValues(event.target);
    await api(`/applications/${currentId}/interviews`, {
      method: 'POST',
      body: {
        stage: values.stage,
        notes: values.notes,
        scheduledAt: values.scheduledAt ? new Date(values.scheduledAt).toISOString() : null,
      },
    });
    event.target.reset();
    await loadChildren();
  }));

  $('reminder-form').addEventListener('submit', guarded(async (event) => {
    const values = formValues(event.target);
    await api(`/applications/${currentId}/reminders`, {
      method: 'POST',
      body: { remindAt: new Date(values.remindAt).toISOString(), message: values.message },
    });
    event.target.reset();
    await loadChildren();
  }));

  render();
}

init();
