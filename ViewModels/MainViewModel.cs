using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Messaging;
using MotionWatch.Events;
using MotionWatch.Models;
using MotionWatch.Services;

namespace MotionWatch.ViewModels;

public partial class MainViewModel : ObservableObject,
    IRecipient<GestoDetectadoMessage>,
    IRecipient<LatenciaMedidaMessage>,
    IRecipient<ConexionCambiadaMessage>
{
    private readonly IGestosService _servicio;

    [ObservableProperty] private string flecha = "•";
    [ObservableProperty] private string latencia = "-- ms";
    [ObservableProperty] private string estado = "Desconectado";

    public ObservableCollection<string> Registro { get; } = new();

    public MainViewModel(IGestosService servicio)
    {
        _servicio = servicio;
        WeakReferenceMessenger.Default.RegisterAll(this);
    }

    public void Iniciar() => _servicio.Iniciar();

    public void Receive(GestoDetectadoMessage m) =>
        MainThread.BeginInvokeOnMainThread(() =>
        {
            Flecha = m.Gesto switch
            {
                Gesto.Arriba => "↑",
                Gesto.Abajo => "↓",
                Gesto.Izquierda => "←",
                Gesto.Derecha => "→",
                _ => "•"
            };
            Registro.Insert(0, $"{m.Fecha:HH:mm:ss}  {Flecha}  {m.Gesto}  ({m.Intensidad})");
            if (Registro.Count > 20) Registro.RemoveAt(Registro.Count - 1);
        });

    public void Receive(LatenciaMedidaMessage m) =>
        MainThread.BeginInvokeOnMainThread(() => Latencia = $"{m.Milisegundos} ms");

    public void Receive(ConexionCambiadaMessage m) =>
        MainThread.BeginInvokeOnMainThread(() => Estado = m.Conectado ? "Conectado" : "Desconectado");
}