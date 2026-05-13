const input = document.getElementById('message-input');
const btn   = document.getElementById('send-btn');
const msgs  = document.getElementById('messages');

function setEnabled(on) {
  input.disabled = !on;
  btn.disabled   = !on;
  if (on) input.focus();
}

function sendMessage() {
  const text = input.value.trim();
  if (!text) return;

  const userDiv = document.createElement('div');
  userDiv.className = 'message user';
  userDiv.textContent = text;
  msgs.appendChild(userDiv);

  const asstDiv = document.createElement('div');
  asstDiv.className = 'message assistant';
  asstDiv.dataset.raw = '';
  msgs.appendChild(asstDiv);
  msgs.scrollTop = msgs.scrollHeight;

  input.value = '';
  setEnabled(false);

  let done = false;
  const es = new EventSource('/stream?q=' + encodeURIComponent(text));

  es.addEventListener('token', function(evt) {
    asstDiv.dataset.raw += evt.data;
    asstDiv.innerHTML = marked.parse(asstDiv.dataset.raw);
    msgs.scrollTop = msgs.scrollHeight;
  });

  es.addEventListener('done', function() {
    done = true;
    es.close();
    setEnabled(true);
  });

  es.addEventListener('error', function() {
    if (!done) {
      es.close();
      asstDiv.innerHTML += '<p><em>[connection error]</em></p>';
      setEnabled(true);
    }
  });
}

btn.addEventListener('click', sendMessage);
input.addEventListener('keydown', function(e) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
});
