import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const App());

const _ch = MethodChannel('com.mydev.photoslide/ch');

class App extends StatelessWidget {
  const App({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Photo Slide',
    theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
    home: const Home(),
    debugShowCheckedModeBanner: false,
  );
}

class Album {
  final String id, name, thumb;
  Album(this.id, this.name, this.thumb);
}

class Home extends StatefulWidget {
  const Home({super.key});
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  List<Album> albums = [];
  int photoCount = 0;
  bool loading = true;
  String? selectedAlbum;

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    try {
      final raw = await _ch.invokeMethod('getAlbums') as String;
      final count = await _ch.invokeMethod('getCount') as int;
      final list = jsonDecode(raw) as List;
      setState(() {
        albums = list.map((a) => Album(a['id'], a['name'], a['thumb'])).toList();
        photoCount = count;
        loading = false;
      });
    } catch (e) { setState(() => loading = false); }
  }

  Future<void> _selectAlbum(Album album) async {
    showDialog(context: context, barrierDismissible: false, builder: (_) => const AlertDialog(
      content: Row(children: [CircularProgressIndicator(), SizedBox(width: 16), Text('Loading photos...')]),
    ));
    try {
      final count = await _ch.invokeMethod('selectAlbum', {'bucketId': album.id}) as int;
      if (mounted) Navigator.pop(context);
      setState(() { selectedAlbum = album.name; photoCount = count; });
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Selected ${album.name}: $count photos'), backgroundColor: Colors.green));
    } catch (e) {
      if (mounted) Navigator.pop(context);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Photo Slide'),
      actions: [
        IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        IconButton(icon: const Icon(Icons.widgets_outlined), tooltip: 'Add Widget', onPressed: () => _ch.invokeMethod('pinWidget')),
      ],
    ),
    body: loading ? const Center(child: CircularProgressIndicator()) : ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // معلومات الحالة
        Card(color: photoCount > 0 ? Colors.green[50] : Colors.orange[50], child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(children: [
            Icon(photoCount > 0 ? Icons.check_circle : Icons.info_outline,
              color: photoCount > 0 ? Colors.green : Colors.orange, size: 32),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(photoCount > 0 ? 'Widget Active' : 'No photos selected',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              if (photoCount > 0) Text('$photoCount photos • Changes every 5 min', style: const TextStyle(color: Colors.grey)),
              if (selectedAlbum != null) Text('Album: $selectedAlbum', style: const TextStyle(color: Colors.grey)),
            ])),
          ]),
        )),
        const SizedBox(height: 16),

        // أزرار
        Row(children: [
          Expanded(child: ElevatedButton.icon(
            onPressed: () => _ch.invokeMethod('pinWidget'),
            icon: const Icon(Icons.add_to_home_screen),
            label: const Text('Add to Home'),
            style: ElevatedButton.styleFrom(backgroundColor: Colors.indigo, foregroundColor: Colors.white),
          )),
          const SizedBox(width: 8),
          Expanded(child: ElevatedButton.icon(
            onPressed: () async {
              await _ch.invokeMethod('pickPhotos');
              await Future.delayed(const Duration(seconds: 2));
              _load();
            },
            icon: const Icon(Icons.add_photo_alternate),
            label: const Text('Add Photos'),
          )),
        ]),
        const SizedBox(height: 20),

        // الألبومات
        const Text('Select Album', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        const Text('Tap an album to use it in the widget', style: TextStyle(color: Colors.grey)),
        const SizedBox(height: 12),

        if (albums.isEmpty)
          const Center(child: Padding(padding: EdgeInsets.all(32), child: Column(children: [
            Icon(Icons.photo_library_outlined, size: 64, color: Colors.grey),
            SizedBox(height: 12),
            Text('No albums found', style: TextStyle(color: Colors.grey, fontSize: 16)),
            SizedBox(height: 4),
            Text('Make sure photos are on your device', style: TextStyle(color: Colors.grey)),
          ])))
        else
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2, crossAxisSpacing: 10, mainAxisSpacing: 10, childAspectRatio: 1.1),
            itemCount: albums.length,
            itemBuilder: (_, i) {
              final a = albums[i];
              final isSelected = selectedAlbum == a.name;
              return GestureDetector(
                onTap: () => _selectAlbum(a),
                child: Card(
                  clipBehavior: Clip.antiAlias,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                    side: BorderSide(color: isSelected ? Colors.indigo : Colors.transparent, width: 3),
                  ),
                  child: Stack(children: [
                    Container(color: Colors.grey[200], child: const Center(child: Icon(Icons.photo_album, size: 56, color: Colors.indigo))),
                    Positioned(bottom: 0, left: 0, right: 0, child: Container(
                      color: Colors.black54,
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                      child: Text(a.name, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold), maxLines: 1, overflow: TextOverflow.ellipsis),
                    )),
                    if (isSelected) Positioned(top: 8, right: 8, child: Container(
                      decoration: const BoxDecoration(color: Colors.indigo, shape: BoxShape.circle),
                      padding: const EdgeInsets.all(4),
                      child: const Icon(Icons.check, color: Colors.white, size: 16),
                    )),
                  ]),
                ),
              );
            },
          ),
      ],
    ),
  );
}
