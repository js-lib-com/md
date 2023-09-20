# Prezentare generală

Call Radar colectează în timp real date despre apelurile centrului de contact de urgență și afișează apelurile pe o hartă. Un apel este un obiect viu care este actualizat constant cu informații de la OMS. Are un ciclu de viață de 24 de ore, după care este eliminat din interfața cu utilizatorul. Un apel este reprezentat pe hartă printr-o pictogramă, cu o culoare care reflectă starea apelului și ciclul de viață curent.

Peste hartă, Call Radar afișează rețeaua electrică, încărcată de pe serverul ArcGIS.

Call Radar este o aplicație web cu arhitectură client-server. Interfața de utilizator este afișată într-un browser web standard, toate versiunile curente de browser fiind acceptate. Paginile aplicației sunt încărcate de pe server de la adresa https://callradar.eonsn.ro/.

Back-end-ul aplicației rulează pe server și se ocupă de colectarea și stocarea apelurilor în baza de date. Apelurile din baza de date sunt actualizate în timp real de un automat care rulează pe server.

Back-end-ul este, de asemenea, responsabil pentru transmiterea apelurilor și a modificărilor de stare a interfeței cu utilizatorul, în timp real. Ca răspuns la evenimentele de pe server, interfața de utilizator este actualizată.

# Conectare la aplicație

Deoarece Call Radar se ocupă de date critice despre rețeaua electrică, folosește o conexiune sigură cu serverul și necesită autentificarea utilizatorului.

Aplicația folosește un formular standard de conectare a utilizatorului și necesită acreditări definite pe serverul Active Directory (AD) de la Special Network (SN).
 
![Formular de conectare](login-form.png)
 
În formularul de autentificare, utilizatorul trebuie să introducă numai numele său de utilizator, fără sufix. De exemplu, irotaru dar nu irotaru@eonsn.ro .

Dacă acreditările nu sunt acceptate, nu există feedback pe formularul de conectare al utilizatorului pentru a minimiza șansele de a ghici.

După conectarea cu succes, aplicația este încărcată de pe server. În cazul unei parole greșite, formularul de conectare este afișat din nou. Numărul de încercări este limitat în conformitate cu politica AD.

Dacă tot nu vă autentificați după mai multe încercări, este posibil să aveți probleme cu contul și trebuie să contactați administratorul AD.

# Interfața cu utilizatorul

Versiunea curentă a aplicației are o singură pagină care este deschisă după autentificarea cu succes. Afișează apelurile efectuate pe centrul de contact de urgență, transmise de server în timp real.

La pornirea aplicației sau la reîncărcarea paginii, harta apelurilor este încărcată din baza de date a serverului, apoi aplicația așteaptă noi apeluri. Când sosește un apel, back-end-ul serverului este informat de centrul de contact și datele apelului sunt trimise în browser.

Un apel are un ciclu de viață care durează 24 de ore. În prezent există patru faze; culoarea pictogramei se schimbă atunci când apelul își schimbă starea. De asemenea, culoarea pictogramei este actualizată dacă există un bilet OMS legat de apel.

| Culoare | Descriere |
|--------|--------------|
| Verde | Apelul are mai puțin de 2 ore. |
| Albastru | Apelul este între 2 și 14 ore. |
| Roșu | Apelul este între 14 și 22 de ore. |
| Negru | Ultimele 2 ore înainte de a elimina apelul din interfața de utilizator. |
| Violet | Biletul OMS creat la punctul de livrare atașat apelului. |
| Galben | Biletul OMS rezolvat; va fi eliminat din interfața de utilizator după 2 ore. |

Apelul este eliminat din interfața utilizator când ciclul său de viață este încheiat. De asemenea, apelul este eliminat atunci când OMS declară biletul rezolvat. Odată ce a fost eliminată o interacțiune a apelului, aceasta poate fi afișată numai utilizând selecția perioadei de timp din meniul principal.

![Interfață utilizator](user-interface.png)
 
Interfața cu utilizatorul are trei componente principale:
- Meniul principal în partea de sus a paginii,
- Harta apelurilor ocupă zona centrală a ecranului,
- Detalii despre apeluri în partea dreaptă a ecranului.

Fereastra cu detaliile apelurilor are trei vizualizări:
- Jurnalul de apeluri afișează toate apelurile primite ca o listă,
- Arborele rețelei afișează un arbore de elemente de rețea electrică,
- Ajutor context, deocamdată doar codul de culori pentru pictograme.

Ferestrele cu detalii sunt exclusive; doar una este vizibil la un moment dat. Utilizatorul poate selecta vizualizarea activă folosind pictogramele din meniul principal.

## Meniu principal

Meniul principal din partea de sus a paginii are controale pentru selecția perioadei de timp, selectoare pentru filtrare, pictograme pentru vizualizări detaliate și nivelul de zoom pentru arborele rețelei electrice.

Pentru a selecta perioada de timp, utilizatorul trebuie să selecteze data și ora de început și de sfârșit. Există două selectoare de date standard în ordine naturală. Dacă utilizatorul preferă acest lucru, există opțiunea de a introduce valori numerice folosind formatul indicat. Ora folosește formatul de 12 ore, cu indicator AM/PM.

După introducerea perioadei de timp, apăsați pe pictograma prima deoparte pentru a începe încărcarea apelurilor. Pentru a șterge perioada de timp, apăsați pictograma următoare.

Selectoarele de filtrare sunt folosite pentru a restrânge numărul de apeluri afișate pe interfața de utilizator. Pentru a aplica filtrarea, utilizatorul trebuie doar să selecteze opțiunile de filtrare.

Există trei vizualizări detaliate în partea dreaptă a ecranului, fiecare având propria pictogramă:
- Afișează jurnalul de apeluri,
- Afișează arborele rețelei electrice,
- Afișează ajutor contextual.

Când arborele rețelei electrice este activ, puteți utiliza ultimele două pictograme din meniul principal pentru a extinde și restrânge toate elementele rețelei.

## Harta apelurilor

Harta apelurilor se bazează pe Open Street Maps și un apel este afișat ca o pictogramă de balon. Coordonatele pictogramei sunt încărcate din baza de date pe baza numărului de telefon al clientului. Peste hartă există și un strat cu rețeaua electrică, încărcată de pe serverul ArcGIS.

Harta are comenzi pentru nivelul de zoom. Modificarea nivelului de zoom poate fi efectuată și folosind rotița mouse-ului. Când se schimbă nivelul de zoom sau se trage harta, vizualizările din partea dreaptă sunt actualizate pentru a afișa numai apelurile care sunt efectiv vizibile pe hartă.

## Jurnal de apeluri

Jurnalul de apeluri este o listă simplă de text afișată în partea dreaptă a paginii; o înregistrare a apelurilor este tipărită pe o singură linie. Înregistrările din jurnal sunt afișate sus/jos, cu cea mai recentă interacțiune a apelurilor în partea de sus.

Versiunea curentă afișează următoarele informații:
- Timpul apelului este ora la care a fost introdus apelul în centrul de contact de urgență,
- Numărul de telefon al clientului detectat de centrul de contact,
- Adresa postala compusa dupa judet, localitate si strada.

Adresa poștală este fie încărcată din baza de date pe baza numărului de telefon al clientului, fie înregistrată din apelul clientului, dacă fluxul de apeluri ajunge în punctul în care centrul de contact solicită adresa.

## Arborele rețelei electrice

Arborele rețelei electrice afișează elemente electrice legate de apelurile curente. Este o vizualizare arborescentă standard similară în utilizare cu exploratorul de fișiere din Windows.

Elementele rețelei sunt afișate grupate pe: regiune, substație, linie de medie tensiune, transformator și linie de distribuție de joasă tensiune. Există o pictogramă pe fiecare element al grilei; atunci când faceți clic pe el, elementul grilă este extins sau restrâns.

![Grid Tree](grid-tree.png)

Dacă doriți să extindeți sau să restrângeți toate elementele grilei din ierarhie, faceți clic pe nodul dorit în timp ce apăsați tasta de control. De exemplu, faceți clic pe + ctrl pe regiunea restrânsă va extinde toate substațiile și toate celelalte elemente din ierarhie.

## Filtrare

Filtrele sunt folosite pentru a restrânge numărul de apeluri afișate pe interfața de utilizator. Pentru a aplica filtrarea, utilizatorul trebuie doar să selecteze opțiunile de filtrare din selector. Filtrele sunt cumulate; dacă selectați mai multe filtre sunt afișate numai apelurile care satisfac toate filtrele.

Când filtrarea este activă, toate vizualizările sunt afectate: harta apelurilor, jurnalele și arborele rețelei electrice. Pentru a elimina un filtru activ, selectați prima opțiune din selectorul de filtru.

## Filtrarea timpului

Filtrați după vârsta apelului. Dacă utilizați acest filtru, vor fi afișate numai apelurile mai recente decât valoarea de timp selectată.
 
![Time Filter](time-filter.png)

## Filtrare de tip

Permite afișarea numai a apelurilor sau numai a biletelor OMS.
 
![Filtru de întrerupere](type-filter.png)

## Filtrarea adresei

Filtrul de adresă este un filtru compus care modifică funcția în vizualizarea curentă a detaliilor active.

Filtrul de județ este vizibil când jurnalul de apeluri este activ.

![Filtru de județ](county-filter.png)

Când vizualizarea pentru rețeaua electrică este activă, filtrul transformatorului permite afișarea doar a unui anumit transformator.

![Filtru de transformator](transformer-filter.png)

# Sincronizare hărți

Când nivelul de zoom al hărții este schimbat sau harta este trasă, vizualizarea activă din partea dreaptă este actualizată pentru a afișa numai apelurile care sunt efectiv vizibile pe hartă.
