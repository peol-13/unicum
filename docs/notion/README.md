# Programma e registro delle corse su Notion

UNICUM può leggere il programma di allenamento da due database Notion (sola lettura, API ufficiale).
Qui c'è come costruirli: **i nomi delle proprietà devono essere esattamente questi** (maiuscole comprese),
perché l'app li cerca per nome. Le proprietà in più vengono ignorate.

> **English, in short:** create the two Notion databases below with these exact property names and types
> (you can import the CSV files and then change the property types), share their page with an internal
> integration, then paste the integration token and the link of the plan database in
> UNICUM → Profilo → Programma di allenamento → Notion.

## 1. "Programma corse" (obbligatorio)

Una riga per ogni seduta programmata.

| Proprietà | Tipo in Notion | Note |
|---|---|---|
| `Allenamento` | Titolo | Nome della seduta, es. "Ripetute 5×3′" |
| `Data` | Data | Senza data la seduta non compare |
| `Tipo` | Selezione | I tag che vuoi: l'app usa questi (con i loro colori). Consigliati: Facile, Lungo facile, Progressivo, Intervalli, Test, Libera |
| `Stato` | Stato | `Da fare`, `Completata`, `Saltata` (Completata e Saltata chiudono la seduta) |
| `Obiettivo km` | Numero | |
| `Durata min` | Numero | |
| `Ritmo target` | Testo | es. `6:30–6:45/km` |
| `RPE target` | Numero | 1–10 |
| `Struttura` | Testo | Per Intervalli e Progressivo divide la corsa in pezzi, vedi sotto |
| `Focus` | Testo | Facoltativo |
| `Settimana` | Numero | Facoltativo (settimana del ciclo) |
| `Sport` | Selezione | Facoltativo: `Corsa`, `Pesi` o `Corpo libero`. Se manca, l'app lo capisce dal Tipo o dal nome ("Forza", "Palestra", "Corpo libero"); altrimenti è una corsa |

**Struttura della corsa**: l'app riconosce due forme (durate in `'` minuti, `''` secondi, `km` o `m`):

- ripetute: `10' di corsa facile + 5×(3' a 5:50 + 2' lento) + 5' defaticamento`
- progressivo: `15' progressivi: 5' a 7:10, 5' a 6:50, 5' a 6:30`

**Struttura di Pesi e Corpo libero**: gli esercizi, uno per riga oppure separati da `;`. Per le sedute di forza
`Obiettivo km` e `Ritmo target` restano vuoti.

- serie × ripetizioni e carico: `Panca piana 4x8 @ 60 kg`, `Rematore 3x8-10 40 kg`, `Trazioni 3xmax`
- a tempo: `Plank 3x45''`; recupero: `rec 90''` o `rec 2'`
- circuito: `Circuito 3 giri: 10 piegamenti, 20 squat, 30'' plank` (i giri diventano le serie)

Quando l'orologio registra la seduta, nella pagina dell'allenamento gli esercizi arrivano da qui; se hai fatto
qualcosa di diverso (più kg, meno ripetizioni) li correggi nell'app.

## 2. "Registro corse" (facoltativo)

Una riga per ogni corsa fatta, con le sensazioni. Quando c'è, **tipo, RPE e nota di Notion vincono** su quelli
scritti nell'app; i numeri della corsa (distanza, tempo, battito) arrivano sempre da Health Connect.

| Proprietà | Tipo in Notion | Note |
|---|---|---|
| `Corsa` | Titolo | |
| `Data` | Data | La riga si abbina alla corsa dello stesso giorno |
| `Tipo` | Selezione | Stessi tag del programma |
| `RPE` | Numero | 1–10 |
| `Note` | Testo | |
| `Distanza km` | Numero | Serve solo se nello stesso giorno ci sono più corse |
| `Sessione pianificata` | Relazione → Programma corse | Facoltativa: collega la corsa alla seduta |

Se non incolli il link del registro, l'app lo cerca da sola tra i database condivisi con l'integrazione
(nome con "Registro" o "log", oppure con i campi `RPE` e `Sessione pianificata`).

## Come collegarli

1. Importa [programma-corse.csv](programma-corse.csv) e [registro-corse.csv](registro-corse.csv) in Notion
   (••• → Importa → CSV), oppure crea i database a mano. Dopo l'import controlla i tipi delle proprietà
   (Notion importa tutto come testo): Data → Data, Tipo → Selezione, Stato → Stato, numeri → Numero,
   Sessione pianificata → Relazione.
2. Su [notion.so/profile/integrations](https://www.notion.so/profile/integrations) crea un'integrazione
   interna con il solo permesso di lettura e copia il token (inizia con `ntn_`).
3. Nella pagina che contiene i due database: ••• → Connessioni → aggiungi l'integrazione.
4. In UNICUM: Profilo → Programma di allenamento → Notion: incolla il token e il link del database del
   programma (Condividi → Copia link). Il registro è facoltativo.

Il token resta solo sul telefono; l'app non scrive mai su Notion.
