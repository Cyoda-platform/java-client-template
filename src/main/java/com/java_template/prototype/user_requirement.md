Below is a complete, ready-to-run "Purrfect Pets" app that uses Swagger Petstore API data and adds a playful frontend and a small proxy/adoption endpoint. Copy the files into a project folder and follow the README to run.

Files:

1) package.json
```
{
  "name": "purrfect-pets",
  "version": "1.0.0",
  "description": "Purrfect Pets — playful UI using Petstore API data",
  "main": "server.js",
  "scripts": {
    "start": "node server.js"
  },
  "dependencies": {
    "cors": "^2.8.5",
    "express": "^4.18.2",
    "node-fetch": "^2.6.7"
  }
}
```

2) server.js
```
const express = require('express');
const fetch = require('node-fetch');
const path = require('path');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const PETSTORE_BASE = 'https://petstore.swagger.io/v2';

// Simple in-memory adoption registry (not persistent)
const adoptions = [];

// Proxy: get available pets (maps to Swagger Petstore)
app.get('/api/pets', async (req, res) => {
  try {
    // fetch available pets
    const r = await fetch(`${PETSTORE_BASE}/pet/findByStatus?status=available`);
    const data = await r.json();
    // Ensure array
    const pets = Array.isArray(data) ? data : [];
    // Map to friendly shape
    const mapped = pets.map(p => ({
      id: p.id,
      name: p.name || 'Unnamed',
      category: p.category && p.category.name ? p.category.name : 'pet',
      status: p.status || 'unknown',
      photoUrls: Array.isArray(p.photoUrls) && p.photoUrls.length ? p.photoUrls : [],
      tags: Array.isArray(p.tags) ? p.tags.map(t => t.name) : []
    }));
    res.json(mapped);
  } catch (err) {
    console.error('Error fetching pets:', err);
    res.status(500).json({ error: 'Failed to fetch pets' });
  }
});

// Proxy: get single pet by id
app.get('/api/pets/:id', async (req, res) => {
  try {
    const r = await fetch(`${PETSTORE_BASE}/pet/${encodeURIComponent(req.params.id)}`);
    if (r.status === 404) return res.status(404).json({ error: 'Pet not found' });
    const p = await r.json();
    res.json({
      id: p.id,
      name: p.name || 'Unnamed',
      category: p.category && p.category.name ? p.category.name : 'pet',
      status: p.status || 'unknown',
      photoUrls: Array.isArray(p.photoUrls) && p.photoUrls.length ? p.photoUrls : [],
      tags: Array.isArray(p.tags) ? p.tags.map(t => t.name) : []
    });
  } catch (err) {
    console.error('Error fetching pet:', err);
    res.status(500).json({ error: 'Failed to fetch pet' });
  }
});

// Adopt a pet (local in-memory)
app.post('/api/adopt', (req, res) => {
  const { petId, adopterName } = req.body || {};
  if (!petId || !adopterName) {
    return res.status(400).json({ error: 'petId and adopterName are required' });
  }
  const existing = adoptions.find(a => a.petId === petId);
  if (existing) {
    return res.status(409).json({ error: 'Pet already adopted' });
  }
  const adoption = {
    petId,
    adopterName,
    adoptedAt: new Date().toISOString()
  };
  adoptions.push(adoption);
  res.json({ success: true, adoption });
});

// Get adoptions
app.get('/api/adoptions', (req, res) => {
  res.json(adoptions);
});

// Fallback to index for SPA
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Purrfect Pets running at http://localhost:${PORT}`);
});
```

3) public/index.html
```
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>Purrfect Pets</title>
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <link rel="stylesheet" href="/styles.css" />
</head>
<body>
  <header>
    <h1>🐾 Purrfect Pets</h1>
    <p>Fetches fun pet data from the Petstore API. Adopt a pet and give it a loving home!</p>
  </header>

  <main>
    <section id="controls">
      <button id="refreshBtn">Refresh Available Pets</button>
      <button id="viewAdoptionsBtn">View My Adoptions</button>
    </section>

    <section id="pets">
      <h2>Available Pets</h2>
      <div id="petList" class="grid"></div>
    </section>

    <section id="adoptions" class="hidden">
      <h2>Adoptions</h2>
      <div id="adoptionList"></div>
      <button id="closeAdoptionsBtn">Close</button>
    </section>
  </main>

  <div id="modal" class="modal hidden">
    <div class="modal-content">
      <h3 id="modalTitle">Adopt pet</h3>
      <p id="modalBody">Are you sure?</p>
      <label>
        Your name: <input type="text" id="adopterName" placeholder="Your name" />
      </label>
      <div class="modal-actions">
        <button id="confirmAdoptBtn">Adopt</button>
        <button id="cancelAdoptBtn">Cancel</button>
      </div>
    </div>
  </div>

  <script src="/app.js"></script>
</body>
</html>
```

4) public/styles.css
```
:root {
  --bg: #fffef6;
  --accent: #ff8da1;
  --muted: #6b6b6b;
  --card: #fff;
  --shadow: 0 6px 18px rgba(0,0,0,0.08);
  --radius: 12px;
}

* { box-sizing: border-box; }
body {
  font-family: Inter, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial;
  margin: 0;
  background: linear-gradient(180deg,#fffef6 0%, #fff 100%);
  color: #222;
  padding-bottom: 60px;
}

header {
  text-align: center;
  padding: 28px 18px;
}

h1 { margin: 0; font-size: 2.2rem; }
p { margin: 8px 0 0; color: var(--muted); }

main { max-width: 980px; margin: 18px auto; padding: 0 16px; }

#controls { display:flex; gap:12px; justify-content:center; margin-bottom:18px; }
button {
  background: var(--accent);
  color: white;
  border: none;
  padding: 10px 14px;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: var(--shadow);
}
button.secondary {
  background: #ffd9e0;
  color: #4a2a33;
}

.grid { display:grid; grid-template-columns: repeat(auto-fit, minmax(220px,1fr)); gap:16px; }

.card {
  background: var(--card);
  border-radius: var(--radius);
  padding: 12px;
  box-shadow: var(--shadow);
  display:flex;
  gap:12px;
  align-items: center;
}

.thumb {
  width:88px;
  height:88px;
  border-radius: 10px;
  background: #f4f4f4;
  display:flex;
  align-items:center;
  justify-content:center;
  overflow:hidden;
  flex-shrink:0;
}

.thumb img { width:100%; height:100%; object-fit:cover; }

.info { flex:1; }
.name { font-weight:700; font-size:1.05rem; margin:0; }
.meta { color:var(--muted); margin-top:6px; font-size:0.9rem; }

.actions { display:flex; gap:8px; margin-top:8px; }

.hidden { display:none; }

/* modal */
.modal {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(10,10,10,0.35);
  z-index: 50;
}
.modal-content {
  background: white;
  border-radius: 12px;
  padding: 18px;
  width: 320px;
  box-shadow: var(--shadow);
}
.modal-actions { display:flex; gap:8px; margin-top:12px; justify-content:flex-end; }
label input { width:100%; padding:8px; margin-top:6px; border-radius:8px; border:1px solid #eee; }

#adoptionList { margin-top:10px; }
.adopt-item { padding:8px; border-bottom:1px solid #f0f0f0; }
```

5) public/app.js
```
const petListEl = document.getElementById('petList');
const refreshBtn = document.getElementById('refreshBtn');
const modal = document.getElementById('modal');
const modalTitle = document.getElementById('modalTitle');
const modalBody = document.getElementById('modalBody');
const adopterNameInput = document.getElementById('adopterName');
const confirmAdoptBtn = document.getElementById('confirmAdoptBtn');
const cancelAdoptBtn = document.getElementById('cancelAdoptBtn');
const viewAdoptionsBtn = document.getElementById('viewAdoptionsBtn');
const adoptionsSection = document.getElementById('adoptions');
const adoptionList = document.getElementById('adoptionList');
const closeAdoptionsBtn = document.getElementById('closeAdoptionsBtn');

let currentPetToAdopt = null;

function showModal(pet) {
  currentPetToAdopt = pet;
  modalTitle.textContent = `Adopt ${pet.name} 🐾`;
  modalBody.textContent = `Are you ready to give ${pet.name} a purrfect home?`;
  adopterNameInput.value = '';
  modal.classList.remove('hidden');
}

function hideModal() {
  currentPetToAdopt = null;
  modal.classList.add('hidden');
}

async function fetchPets() {
  petListEl.innerHTML = '<p>Loading pets... 🐱</p>';
  try {
    const res = await fetch('/api/pets');
    const pets = await res.json();

    if (!pets || pets.length === 0) {
      petListEl.innerHTML = '<p>No available pets right now. Try refreshing.</p>';
      return;
    }

    // Optionally, present only "cats" or tag with cat — but we will present all as “Purrfect” for fun.
    petListEl.innerHTML = '';
    pets.forEach(p => {
      const card = document.createElement('div');
      card.className = 'card';

      const thumb = document.createElement('div');
      thumb.className = 'thumb';
      const img = document.createElement('img');
      img.alt = p.name;
      // Use pet photo or fallback fun cat image
      img.src = p.photoUrls && p.photoUrls.length ? p.photoUrls[0] : `https://placekitten.com/400/300?image=${(p.id || Math.floor(Math.random()*10))}`;
      thumb.appendChild(img);

      const info = document.createElement('div');
      info.className = 'info';
      const name = document.createElement('p');
      name.className = 'name';
      name.textContent = p.name + (p.tags && p.tags.includes('cat') ? ' 🐱' : '');
      const meta = document.createElement('p');
      meta.className = 'meta';
      meta.textContent = `Category: ${p.category} • Status: ${p.status}`;

      const actions = document.createElement('div');
      actions.className = 'actions';
      const adoptBtn = document.createElement('button');
      adoptBtn.textContent = 'Adopt 🐾';
      adoptBtn.onclick = () => showModal(p);
      const detailsBtn = document.createElement('button');
      detailsBtn.textContent = 'Details';
      detailsBtn.className = 'secondary';
      detailsBtn.onclick = () => showDetails(p.id);

      actions.appendChild(adoptBtn);
      actions.appendChild(detailsBtn);

      info.appendChild(name);
      info.appendChild(meta);
      info.appendChild(actions);

      card.appendChild(thumb);
      card.appendChild(info);
      petListEl.appendChild(card);
    });
  } catch (err) {
    petListEl.innerHTML = '<p>Error loading pets. Try again.</p>';
    console.error(err);
  }
}

async function showDetails(id) {
  try {
    const res = await fetch(`/api/pets/${encodeURIComponent(id)}`);
    if (!res.ok) {
      alert('Pet not found');
      return;
    }
    const p = await res.json();
    alert(`Name: ${p.name}\nCategory: ${p.category}\nStatus: ${p.status}\nTags: ${p.tags.join(', ')}`);
  } catch (err) {
    console.error(err);
    alert('Failed to fetch details');
  }
}

confirmAdoptBtn.addEventListener('click', async () => {
  const name = adopterNameInput.value.trim();
  if (!name) return alert('Please enter your name');
  if (!currentPetToAdopt) return hideModal();

  try {
    const res = await fetch('/api/adopt', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ petId: currentPetToAdopt.id, adopterName: name })
    });
    const body = await res.json();
    if (!res.ok) {
      alert('Adoption failed: ' + (body && body.error ? body.error : 'unknown'));
    } else {
      alert(`Congrats! You adopted ${currentPetToAdopt.name} 🐾`);
      hideModal();
    }
  } catch (err) {
    console.error(err);
    alert('Adoption failed');
  }
});

cancelAdoptBtn.addEventListener('click', hideModal);
refreshBtn.addEventListener('click', fetchPets);

viewAdoptionsBtn.addEventListener('click', async () => {
  adoptionsSection.classList.remove('hidden');
  adoptionList.innerHTML = '<p>Loading your adoptions...</p>';
  try {
    const res = await fetch('/api/adoptions');
    const data = await res.json();
    if (!data || data.length === 0) {
      adoptionList.innerHTML = '<p>No adoptions yet. Go adopt a pet!</p>';
      return;
    }
    adoptionList.innerHTML = '';
    data.forEach(a => {
      const div = document.createElement('div');
      div.className = 'adopt-item';
      div.innerHTML = `<strong>Pet ID:</strong> ${a.petId} • <strong>Adopter:</strong> ${a.adopterName} • <em>${new Date(a.adoptedAt).toLocaleString()}</em>`;
      adoptionList.appendChild(div);
    });
  } catch (err) {
    adoptionList.innerHTML = '<p>Failed to load adoptions.</p>';
    console.error(err);
  }
});

closeAdoptionsBtn.addEventListener('click', () => adoptionsSection.classList.add('hidden'));

// initial load
fetchPets();
```

6) README.md
```
Purrfect Pets — Quick start

1. Save the files into a folder named purrfect-pets with the structure:
   - package.json
   - server.js
   - public/
     - index.html
     - app.js
     - styles.css

2. Install dependencies:
   npm install

3. Start the app:
   npm start

4. Open in your browser:
   http://localhost:3000

What it does:
- Fetches available pets from the Swagger Petstore API (/pet/findByStatus?status=available).
- Shows the pets with pictures (falls back to cute kitten placeholders).
- Lets you "adopt" a pet — that adoption is stored in memory in the server (non-persistent).
- Provides a small proxy for pet details and a local adoptions endpoint.

Notes:
- Adoptions are stored in server memory and will reset when the server restarts.
- The app is intentionally playful and simple—extend as you like (persistence, authentication, better UI).
```

That's the full Purrfect Pets app using Petstore API data.