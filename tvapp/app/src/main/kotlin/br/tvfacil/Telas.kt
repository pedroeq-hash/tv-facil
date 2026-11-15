package br.tvfacil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import br.tvfacil.core.Biblioteca
import br.tvfacil.core.Camada
import br.tvfacil.core.Busca
import br.tvfacil.core.Canal
import br.tvfacil.core.Catalogo
import br.tvfacil.core.EstadoVideo
import br.tvfacil.core.Fase
import br.tvfacil.core.Grupo
import br.tvfacil.core.Serie
import br.tvfacil.core.Tipo
import kotlinx.coroutines.delay

// Cores e medidas do protótipo aprovado (docs/plano-tvapp.md). A tela do stick tem 853x480 dp (= protótipo / 1,5).
val FUNDO = Color(0xFF0E1116)
val TEXTO = Color(0xFFF4F1EA)
private val FOCO = Color(0xFFFFC94D)
private val SOBRE_FOCO = Color(0xFF1A1300)
private val CINZA = Color(0xFFC3C9D1)
private val PAINEL = Color(0xF20E1116)
private val CARTAO = Color(0xFF1A1F27)
private val BORDA = Color(0xFF3A424E)
private val LATERAL = Color(0xFF141920)
private val SELO = Color(0xFF2A313C)
val ATKINSON = FontFamily(Font(R.font.atkinson_regular), Font(R.font.atkinson_bold, FontWeight.Bold))

private const val BARRA_MS = 6_000L
private val LARGURA_LISTA = 387.dp

interface Acoes {
    fun escolher(canal: Canal, grupo: String, fecharLista: Boolean = true)
    fun tempoEsgotado(canal: Canal)
    fun zapear(passo: Int)
    fun tentarDeNovo()
    fun mostrar(camada: Camada)
    fun alternarFavorito()
    fun voltarAoAnterior()
    fun mudarTermo(texto: String)
    fun assistirFilme(canal: Canal, voltarPara: Camada, grupo: String)
    fun abrirSerie(item: Canal, grupo: String)
    fun mudarTermoGrade(texto: String)
    fun zerar(oQue: String) // pagina de configuracao: "filmes" / "series" (Continuar assistindo) ou "favoritos"
}

/** O que as telas de filmes e series mostram (estado na MainActivity). tempo: lido so na barra, que redesenha 1 vez por segundo. */
class Vod(
    val gruposFilmes: List<Grupo>,
    val grupoFilmes: String,
    val gruposSeries: List<Grupo>,
    val grupoSeries: String,
    val serie: Serie?,
    val temporadas: List<Grupo>,
    val temporadaAtual: String,
    val ultimo: Canal?,
    val pausado: Boolean,
    val toques: Int,
    val tempo: () -> Pair<Long, Long>,
    val previa: Long?, // segurando ◀▶: para onde o filme vai ao soltar
    val termo: String, // busca no topo de Filmes / Series
    val indiceFilmes: List<Pair<Canal, String>>,
    val indiceSeries: List<Pair<Canal, String>>,
    val carregando: Boolean, // lendo filmes e series (1a abertura depois de ligar o stick)
)

@Composable
fun Tela(
    canais: List<Canal>?,
    grupos: List<Grupo>,
    grupoAtual: String,
    favoritos: Set<String>,
    video: EstadoVideo,
    camada: Camada,
    anterior: Canal?,
    faixa: String?,
    esperaMaximaMs: Long,
    termo: String,
    indiceCanais: List<Pair<Canal, String>>,
    vod: Vod,
    player: VlcPlayer,
    acoes: Acoes,
) {
    if (canais == null) {
        Abertura()
        return
    }
    LaunchedEffect(video) {
        val canal = video.canal
        if (video.fase == Fase.ABRINDO && canal != null) {
            delay(esperaMaximaMs)
            acoes.tempoEsgotado(canal)
        }
    }
    val canal = video.canal
    var barra by remember { mutableStateOf(true) }
    LaunchedEffect(canal, camada, vod.toques) {
        barra = true
        delay(BARRA_MS)
        barra = false
    }
    // Com o mouse: clicar no video abre a lista (no controle, e o OK).
    Box(
        Modifier.fillMaxSize().background(FUNDO).pointerInput(camada) {
            detectTapGestures { if (camada == Camada.ASSISTINDO) acoes.mostrar(Camada.LISTA) }
        },
    ) {
        AndroidView(factory = { ctx -> player.criarTela(ctx) }, modifier = Modifier.fillMaxSize())
        // Com a lista aberta, os avisos vao para a area livre a direita dela.
        val livre = if (camada == Camada.LISTA || camada == Camada.BUSCA) LARGURA_LISTA else 0.dp
        when {
            canais.isEmpty() -> Aviso("Lista de canais não encontrada.\n\n/sdcard/iptv/canais_ok.m3u", livre)
            video.fase == Fase.PARADO -> Aviso("Aperte OK para escolher um canal", livre)
            video.fase == Fase.ABRINDO -> Aviso("Abrindo ${canal?.nome}…", livre)
            video.fase == Fase.INDISPONIVEL && canal != null && camada != Camada.FILME ->
                Indisponivel(canal, camada == Camada.ASSISTINDO, livre, acoes)
        }
        when (camada) {
            Camada.ASSISTINDO -> if (barra && canal != null && video.fase != Fase.INDISPONIVEL) {
                val grupo = grupos.firstOrNull { it.nome == grupoAtual }
                val posicao = grupo?.canais?.indexOf(canal) ?: -1
                val onde = if (grupo != null && posicao >= 0) "${grupo.nome} · canal ${posicao + 1} de ${grupo.canais.size}" else canal.grupo
                BarraCanal(canal, canal.url in favoritos, onde, Modifier.align(Alignment.BottomCenter))
            }
            Camada.LISTA -> ListaCanais(grupos, grupoAtual, canal, acoes)
            Camada.BUSCA -> BuscaCanais(indiceCanais, vod.indiceSeries, vod.indiceFilmes, termo, canal, acoes)
            Camada.GRADE -> GradeCanais("Todos os canais", grupos, grupoAtual, canal) { c, g -> acoes.escolher(c, g) }
            // Categorias que o app ja tem na memoria (as escondidas o Painel acrescenta): ler os 30 mil filmes do disco levava 13 s.
            Camada.CONFIGURAR -> Configurar(
                (grupos + vod.gruposFilmes + vod.gruposSeries).map { it.nome }.filter { it != CONTINUAR && it != Catalogo.FAVORITOS },
                acoes::zerar,
            )
            Camada.FILMES, Camada.SERIES -> if (vod.carregando && vod.gruposFilmes.isEmpty() && vod.gruposSeries.isEmpty()) {
                Aviso("Carregando filmes e séries…\n\nAparecem sozinhos em alguns segundos.")
            } else if (camada == Camada.FILMES) GradeCanais(
                "Filmes", vod.gruposFilmes, vod.grupoFilmes, vod.ultimo, selo = null,
                busca = BuscaGrade(vod.termo, "buscar filme", vod.indiceFilmes, acoes::mudarTermoGrade), poster = true,
            ) { c, g ->
                acoes.assistirFilme(c, Camada.FILMES, g)
            }
            else GradeCanais(
                "Séries", vod.gruposSeries, vod.grupoSeries, vod.serie?.comoCanal(), selo = null,
                busca = BuscaGrade(vod.termo, "buscar série", vod.indiceSeries, acoes::mudarTermoGrade), poster = true,
            ) { c, g ->
                acoes.abrirSerie(c, g)
            }
            Camada.SERIE -> GradeCanais(
                vod.serie?.nome ?: "", vod.temporadas, vod.temporadaAtual, vod.ultimo, selo = "ÚLTIMO",
                sigla = { c -> Biblioteca.numero(c.nome).takeIf { it > 0 }?.toString() ?: "▶" },
            ) { c, g -> acoes.assistirFilme(c, Camada.SERIE, g) }
            Camada.FILME -> when {
                video.fase == Fase.INDISPONIVEL -> Aviso("Não deu para abrir.\n\nAperte Voltar.")
                (barra || vod.pausado) && canal != null -> BarraFilme(canal, vod.tempo, vod.previa, vod.pausado, Modifier.align(Alignment.BottomCenter))
            }
            Camada.MENU -> Menu(
                canal, canal?.url in favoritos, anterior, vod.gruposFilmes.isNotEmpty() || vod.carregando, vod.gruposSeries.isNotEmpty() || vod.carregando, acoes,
                Modifier.align(Alignment.CenterEnd),
            )
        }
        if (faixa != null) {
            Text(
                faixa,
                color = TEXTO,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopCenter).background(Color(0xCC000000)).padding(12.dp),
            )
        }
    }
}

@Composable
private fun BarraCanal(canal: Canal, favorito: Boolean, onde: String, modifier: Modifier) {
    Row(
        modifier.padding(start = 32.dp, end = 32.dp, bottom = 27.dp).fillMaxWidth()
            .background(PAINEL, RoundedCornerShape(13.dp)).padding(horizontal = 21.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(19.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(canal.nome, color = TEXTO, fontSize = 29.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (favorito) Text("★", color = FOCO, fontSize = 24.sp)
            }
            Text(onde, color = CINZA, fontSize = 16.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Dica("CH ▲▼", "trocar canal")
            Dica("OK", "ver lista")
            Dica("≡", "menu")
        }
    }
}

// Filme ou episodio: aparece ao apertar uma tecla e fica enquanto estiver pausado.
@Composable
private fun BarraFilme(canal: Canal, tempo: () -> Pair<Long, Long>, previa: Long?, pausado: Boolean, modifier: Modifier) {
    val (agora, duracao) = tempo()
    val pos = previa ?: agora
    Column(
        modifier.padding(start = 32.dp, end = 32.dp, bottom = 27.dp).fillMaxWidth()
            .background(PAINEL, RoundedCornerShape(13.dp)).padding(horizontal = 21.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(canal.nome, color = TEXTO, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f).passando())
            if (previa != null) Text(if (previa >= agora) "⏩" else "⏪", color = FOCO, fontSize = 22.sp)
            else if (pausado) Text("⏸ Pausado", color = FOCO, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(BORDA, RoundedCornerShape(3.dp))) {
            if (duracao > 0) {
                Box(Modifier.fillMaxWidth((pos.toFloat() / duracao).coerceIn(0f, 1f)).fillMaxHeight().background(FOCO, RoundedCornerShape(3.dp)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (duracao > 0) "${relogio(pos)} / ${relogio(duracao)}" else relogio(pos),
                color = if (previa != null) FOCO else CINZA, fontSize = 16.sp, fontWeight = if (previa != null) FontWeight.Bold else null,
                modifier = Modifier.weight(1f),
            )
            Dica("OK", if (pausado) "continuar" else "pausar", 12.sp)
            Dica("◀", "−10 s", 12.sp)
            Dica("▶", "+30 s", 12.sp)
            Dica("segure", "rápido", 12.sp)
            Dica("Voltar", "sair", 12.sp)
        }
    }
}

// Nome que nao cabe: anda devagar, para um pouco no comeco e anda de novo. So no item em foco: o stick e fraco.
private fun Modifier.passando() = basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_500, repeatDelayMillis = 2_000)

private fun relogio(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Dica(tecla: String, texto: String, tamanho: TextUnit = 13.sp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tecla,
            color = TEXTO,
            fontSize = tamanho,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.background(BORDA, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text(texto, color = CINZA, fontSize = tamanho, maxLines = 1)
    }
}

@Composable
private fun ListaCanais(grupos: List<Grupo>, grupoAtual: String, atual: Canal?, acoes: Acoes) {
    if (grupos.isEmpty()) return
    var indice by remember { mutableIntStateOf(grupos.indexOfFirst { it.nome == grupoAtual }.coerceAtLeast(0)) }
    fun vizinho(passo: Int) = Math.floorMod(indice + passo, grupos.size)
    val grupo = grupos[indice.coerceIn(grupos.indices)]
    val alvo = grupo.canais.indexOf(atual).coerceAtLeast(0)
    val rolagem = rememberLazyListState()
    val foco = remember { FocusRequester() }
    LaunchedEffect(grupo.nome) {
        rolagem.scrollToItem(alvo)
        delay(50) // espera o item aparecer na tela para poder receber o foco
        runCatching { foco.requestFocus() }
    }
    Column(
        Modifier.fillMaxHeight().width(LARGURA_LISTA).background(PAINEL).padding(horizontal = 21.dp, vertical = 24.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { indice = vizinho(-1); true }
                    Key.DirectionRight -> { indice = vizinho(+1); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "◀ ${grupos[vizinho(-1)].nome}", color = CINZA, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).pointerInput(grupos) { detectTapGestures { indice = vizinho(-1) } },
            )
            Text(grupo.nome, color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                "${grupos[vizinho(+1)].nome} ▶", color = CINZA, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).pointerInput(grupos) { detectTapGestures { indice = vizinho(+1) } },
            )
        }
        LazyColumn(state = rolagem, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(grupo.canais) { i, c ->
                // 1º OK so troca o video (da para ir olhando); OK no canal que ja esta no ar fecha a lista.
                ItemCanal(c, noAr = c == atual, modifier = if (i == alvo) Modifier.focusRequester(foco) else Modifier) {
                    if (c == atual) acoes.mostrar(Camada.ASSISTINDO) else acoes.escolher(c, grupo.nome, fecharLista = false)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Dica("▲▼", "escolher", 12.sp)
            Dica("◀ ▶", "grupo", 12.sp)
            Dica("OK", "assistir", 12.sp)
            Dica("OK de novo", "fechar", 12.sp)
            Dica("Voltar", "sair", 12.sp)
        }
    }
}

@Composable
private fun BuscaCanais(
    canais: List<Pair<Canal, String>>,
    series: List<Pair<Canal, String>>,
    filmes: List<Pair<Canal, String>>,
    termo: String,
    atual: Canal?,
    acoes: Acoes,
) {
    val resultados = remember(canais, series, filmes, termo) { Busca.global(canais, series, filmes, termo) }
    val campo = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { campo.requestFocus() }
    }
    Column(
        Modifier.fillMaxHeight().width(LARGURA_LISTA).background(PAINEL).padding(horizontal = 21.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text("Buscar", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        CampoBusca(termo, "canal, filme ou série", acoes::mudarTermo, Modifier.fillMaxWidth().focusRequester(campo))
        when {
            termo.isBlank() -> Text("Aperte OK para digitar o nome do canal, do filme ou da série.", color = CINZA, fontSize = 16.sp)
            resultados.isEmpty() -> Text("Nada com “$termo”", color = CINZA, fontSize = 16.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(resultados) { c ->
                // Mesma regra da lista: 1º OK troca o video, OK no canal que ja esta no ar fecha.
                val detalhe = when (c.tipo) {
                    Tipo.AO_VIVO -> Catalogo.grupoDe(c)
                    Tipo.FILME -> "Filme · ${c.grupo}"
                    Tipo.SERIE -> "Série · ${c.grupo}"
                }
                ItemCanal(c, noAr = c == atual, Modifier, detalhe = detalhe) {
                    when {
                        c.tipo == Tipo.FILME -> acoes.assistirFilme(c, Camada.BUSCA, c.grupo)
                        c.tipo == Tipo.SERIE -> acoes.abrirSerie(c, c.grupo)
                        c == atual -> acoes.mostrar(Camada.ASSISTINDO)
                        else -> acoes.escolher(c, Catalogo.grupoDe(c), fecharLista = false)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Dica("OK", "digitar", 12.sp)
            Dica("▼", "resultados", 12.sp)
            Dica("OK de novo", "fechar", 12.sp)
        }
    }
}

// OK abre o teclado do sistema; ▼ vai para os resultados.
@Composable
private fun CampoBusca(termo: String, dica: String, aoMudar: (String) -> Unit, modifier: Modifier, tamanho: TextUnit = 22.sp) {
    var focado by remember { mutableStateOf(false) }
    BasicTextField(
        value = termo,
        onValueChange = aoMudar,
        singleLine = true,
        textStyle = TextStyle(color = TEXTO, fontSize = tamanho, fontFamily = ATKINSON),
        cursorBrush = SolidColor(FOCO),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.onFocusChanged { focado = it.isFocused }
            .border(3.dp, if (focado) FOCO else BORDA, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { texto ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconeBusca(if (focado) FOCO else CINZA, Modifier.size(with(LocalDensity.current) { tamanho.toDp() }))
                Box(Modifier.weight(1f)) {
                    if (termo.isEmpty()) Text(if (focado) "$dica (OK para digitar)" else dica, color = CINZA, fontSize = tamanho, maxLines = 1)
                    texto()
                }
            }
        },
    )
}

// Lupa ao lado de linhas, so com tracos (no estilo das telas, sem emoji). Desenhada: nao precisa de arquivo de icone.
@Composable
private fun IconeBusca(cor: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension // proporcoes do desenho de referencia (quadro de 960)
        val traco = Stroke(width = u * 0.075f, cap = StrokeCap.Round)
        fun linha(y: Float, x2: Float) = drawLine(cor, Offset(u * 0.15f, u * y), Offset(u * x2, u * y), traco.width, StrokeCap.Round)
        linha(0.22f, 0.56f)
        linha(0.41f, 0.34f)
        linha(0.60f, 0.31f)
        linha(0.79f, 0.41f)
        drawCircle(cor, radius = u * 0.2f, center = Offset(u * 0.64f, u * 0.56f), style = traco)
        drawLine(cor, Offset(u * 0.79f, u * 0.71f), Offset(u * 0.87f, u * 0.79f), traco.width, StrokeCap.Round)
    }
}

@Composable
private fun ItemCanal(canal: Canal, noAr: Boolean, modifier: Modifier, detalhe: String? = null, onClick: () -> Unit) {
    var focado by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focado = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = coresFoco(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    canal.nome, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = if (focado) TextOverflow.Clip else TextOverflow.Ellipsis, modifier = if (focado) Modifier.passando() else Modifier,
                )
                if (detalhe != null) Text(detalhe, fontSize = 13.sp, maxLines = 1)
            }
            if (noAr) NoAr()
        }
    }
}

@Composable
private fun NoAr(modifier: Modifier = Modifier, texto: String = "NO AR") {
    Text(
        texto, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(Color(0xFFE5484D), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Busca no topo da grade (Filmes, Series): so naquele tipo. */
class BuscaGrade(val termo: String, val dica: String, val indice: List<Pair<Canal, String>>, val aoMudar: (String) -> Unit)

// Tela cheia (cobre o video; o som continua): grupos a esquerda, cartoes a direita. OK no cartao assiste e fecha.
// Serve para canais, filmes, series e episodios. selo: marca no cartao "atual" (null = sem marca, so o foco).
@Composable
private fun GradeCanais(
    titulo: String,
    grupos: List<Grupo>,
    grupoAtual: String,
    atual: Canal?,
    selo: String? = "NO AR",
    sigla: (Canal) -> String = { Catalogo.sigla(it.nome) },
    busca: BuscaGrade? = null,
    poster: Boolean = false, // filmes e series: capa em pe, 6 por fileira
    aoEscolher: (Canal, String) -> Unit,
) {
    if (grupos.isEmpty()) return
    var indice by remember { mutableIntStateOf(grupos.indexOfFirst { it.nome == grupoAtual }.coerceAtLeast(0)) }
    val termo = busca?.termo.orEmpty()
    val resultados = remember(busca?.indice, termo) { busca?.let { Busca.filtrar(it.indice, termo) }.orEmpty() }
    // Com termo: a grade mostra os resultados (de todas as categorias) no lugar do grupo.
    val buscando = termo.isNotBlank()
    val grupo = if (buscando) Grupo("Resultados para “${termo.trim()}”", resultados) else grupos[indice.coerceIn(grupos.indices)]
    val alvo = grupo.canais.indexOf(atual).coerceAtLeast(0)
    val rolagem = rememberLazyGridState()
    val foco = remember { FocusRequester() }
    val grupoFoco = remember { FocusRequester() }
    var abriu by remember { mutableStateOf(false) }
    LaunchedEffect(grupo.nome) {
        rolagem.scrollToItem(alvo)
        if (abriu) return@LaunchedEffect
        abriu = true
        delay(50) // espera o cartao aparecer na tela para poder receber o foco
        runCatching { foco.requestFocus() }
    }
    Row(Modifier.fillMaxSize().background(FUNDO)) {
        // Padrao de TV: o grupo que recebe o foco (▲▼ nesta coluna) e o que aparece na grade.
        Column(
            Modifier.fillMaxHeight().width(200.dp).background(LATERAL).verticalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 24.dp)
                .focusRestorer(grupoFoco), // vindo da grade com ◀, cai no grupo atual
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(titulo, color = TEXTO, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 11.dp, bottom = 9.dp))
            grupos.forEachIndexed { i, g ->
                val escolhido = i == indice
                Surface(
                    onClick = { indice = i },
                    modifier = Modifier.fillMaxWidth().then(if (escolhido) Modifier.focusRequester(grupoFoco) else Modifier)
                        .onFocusChanged {
                            if (!it.isFocused) return@onFocusChanged
                            indice = i
                            if (termo.isNotEmpty()) busca?.aoMudar?.invoke("") // foi para as categorias: sai da busca
                        },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (escolhido) SELO else Color.Transparent,
                        contentColor = if (escolhido) TEXTO else CINZA,
                        focusedContainerColor = FOCO,
                        focusedContentColor = SOBRE_FOCO,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.nome, fontSize = 16.sp, fontWeight = if (escolhido) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${g.canais.size}", fontSize = 12.sp)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 27.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            // Acima de tudo: ▲ na 1ª fileira de cartoes ou no 1º grupo da esquerda chega aqui.
            if (busca != null) CampoBusca(termo, busca.dica, busca.aoMudar, Modifier.fillMaxWidth(), tamanho = 18.sp)
            Text(grupo.nome, color = TEXTO, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (buscando && resultados.isEmpty()) Text("Nada com “${termo.trim()}”", color = CINZA, fontSize = 16.sp)
            LazyVerticalGrid(
                GridCells.Fixed(if (poster) 6 else 4),
                state = rolagem,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                items(grupo.canais.size) { i ->
                    val c = grupo.canais[i]
                    val focoInicial = if (i == alvo) Modifier.focusRequester(foco) else Modifier
                    val escolher = { aoEscolher(c, if (buscando) c.grupo else grupo.nome) } // resultado: volta para a categoria certa
                    if (poster) CartaoPoster(c, sigla(c), if (c == atual) selo else null, focoInicial, escolher)
                    else CartaoCanal(c, sigla(c), if (c == atual) selo else null, focoInicial, escolher)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dica("◀ ▶ ▲ ▼", "escolher", 12.sp)
                Dica("OK", "assistir", 12.sp)
                Dica("Voltar", "fechar", 12.sp)
            }
        }
    }
}

// Capa 2:3 com o nome embaixo (desliza quando em foco); sem capa, a sigla no lugar. Foco = borda amarela, sem aumentar.
@Composable
private fun CartaoPoster(canal: Canal, sigla: String, selo: String?, focoInicial: Modifier, onClick: () -> Unit) {
    var focado by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            modifier = focoInicial.fillMaxWidth().aspectRatio(2f / 3f).onFocusChanged { focado = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = CARTAO, contentColor = TEXTO, focusedContainerColor = SELO, focusedContentColor = TEXTO),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FOCO))),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Box(Modifier.fillMaxSize()) {
                Capa(canal.logo, Modifier.fillMaxSize()) {
                    Text(sigla, color = if (focado) FOCO else CINZA, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (selo != null) NoAr(Modifier.align(Alignment.TopEnd).padding(4.dp), selo)
            }
        }
        Text(
            canal.nome, color = if (focado) TEXTO else CINZA, fontSize = 12.sp, fontWeight = if (focado) FontWeight.Bold else null, maxLines = 1,
            overflow = if (focado) TextOverflow.Clip else TextOverflow.Ellipsis, modifier = if (focado) Modifier.passando() else Modifier,
        )
    }
}

@Composable
private fun CartaoCanal(canal: Canal, sigla: String, selo: String?, modifier: Modifier, onClick: () -> Unit) {
    var focado by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(100.dp).onFocusChanged { focado = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = CARTAO, contentColor = TEXTO, focusedContainerColor = SELO, focusedContentColor = TEXTO),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FOCO))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f), // sem aumentar: o stick e fraco
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(43.dp).background(if (focado) FOCO else SELO, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(sigla, color = if (focado) SOBRE_FOCO else TEXTO, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (focado) Text(canal.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.passando())
                else Text(canal.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selo != null) NoAr(Modifier.align(Alignment.TopEnd).padding(5.dp), selo)
        }
    }
}

@Composable
private fun Menu(canal: Canal?, favorito: Boolean, anterior: Canal?, temFilmes: Boolean, temSeries: Boolean, acoes: Acoes, modifier: Modifier) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { foco.requestFocus() }
    }
    // Ate 7 botoes nao cabem nos 480 dp da tela: a coluna rola junto com o foco.
    Column(
        modifier.fillMaxHeight().width(320.dp).background(PAINEL).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Menu", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (canal != null && canal.tipo == Tipo.AO_VIVO) Text(canal.nome, color = CINZA, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Dica("Voltar", "fechar o menu")
        Spacer(Modifier.height(4.dp))
        Botao("Buscar", Modifier.focusRequester(foco)) { acoes.mostrar(Camada.BUSCA) }
        Botao("Todos os canais") { acoes.mostrar(Camada.GRADE) }
        if (temFilmes) Botao("Filmes") { acoes.mostrar(Camada.FILMES) }
        if (temSeries) Botao("Séries") { acoes.mostrar(Camada.SERIES) }
        if (canal != null) {
            Botao(if (favorito) "Tirar dos favoritos" else "★ Favoritar") { acoes.alternarFavorito() }
        }
        if (anterior != null) {
            Botao("Canal anterior:\n${anterior.nome}") { acoes.voltarAoAnterior() }
        }
        Botao("Configurar") { acoes.mostrar(Camada.CONFIGURAR) }
    }
}

// Enquanto esta tela esta aberta, a TV aceita a configuracao pelo navegador do celular (PIN). Fechou, fechou a porta.
@Composable
private fun Configurar(categorias: List<String>, aoZerar: (String) -> Unit) {
    val context = LocalContext.current
    val servidor = remember { ServidorConfig(context, categorias, aoZerar) }
    DisposableEffect(Unit) {
        servidor.iniciar()
        onDispose { servidor.parar() }
    }
    Column(
        Modifier.fillMaxSize().background(FUNDO).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Configurar", color = TEXTO, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        val ip = servidor.ip
        if (ip == null) {
            Text("A TV não está conectada a uma rede Wi-Fi.", color = TEXTO, fontSize = 22.sp)
        } else {
            Text("1. Conecte o celular ou o notebook no mesmo Wi-Fi da TV.", color = CINZA, fontSize = 20.sp)
            Text("2. No navegador, abra:", color = CINZA, fontSize = 20.sp)
            Text("http://$ip:${ServidorConfig.PORTA}", color = FOCO, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("3. Digite o PIN:", color = CINZA, fontSize = 20.sp)
            Text(servidor.pin, color = FOCO, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text(servidor.status, color = TEXTO, fontSize = 20.sp)
        }
        Spacer(Modifier.weight(1f))
        Dica("Voltar", "fechar (a configuração para de funcionar)")
    }
}

@Composable
private fun Indisponivel(canal: Canal, ativo: Boolean, livre: Dp, acoes: Acoes) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(canal, ativo) {
        if (!ativo) return@LaunchedEffect
        delay(50)
        runCatching { foco.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(FUNDO).padding(start = livre).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 507.dp).background(CARTAO, RoundedCornerShape(16.dp)).padding(horizontal = 37.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text("Este canal não está passando agora", color = TEXTO, fontSize = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("${canal.nome} · ${canal.grupo}", color = CINZA, fontSize = 17.sp, textAlign = TextAlign.Center)
            // Sem os botoes quando a lista ou o menu estao abertos: o foco nao "escapa" para eles.
            if (ativo) {
                Spacer(Modifier.height(8.dp))
                Botao("Próximo canal ▶", Modifier.focusRequester(foco)) { acoes.zapear(+1) }
                Botao("Tentar de novo") { acoes.tentarDeNovo() }
                Botao("Ver lista de canais") { acoes.mostrar(Camada.LISTA) }
            }
        }
    }
}

@Composable
private fun Botao(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = coresFoco(),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(3.dp, BORDA)),
            focusedBorder = Border(BorderStroke(3.dp, FOCO)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(texto, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(11.dp))
    }
}

@Composable
private fun coresFoco() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = TEXTO,
    focusedContainerColor = FOCO,
    focusedContentColor = SOBRE_FOCO,
    pressedContainerColor = FOCO,
    pressedContentColor = SOBRE_FOCO,
)

@Composable
fun Abertura() {
    Column(
        Modifier.fillMaxSize().background(FUNDO),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.banner), contentDescription = null, Modifier.size(320.dp, 180.dp))
        Text("TV Fácil", color = TEXTO, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text("Carregando canais…", color = TEXTO, fontSize = 24.sp)
    }
}

@Composable
private fun Aviso(texto: String, livre: Dp = 0.dp) {
    Box(Modifier.fillMaxSize().background(FUNDO).padding(start = livre).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = TEXTO, fontSize = 28.sp, textAlign = TextAlign.Center)
    }
}
