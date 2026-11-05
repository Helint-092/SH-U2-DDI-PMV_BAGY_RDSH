using CommunityToolkit.Mvvm.Messaging;
using MotionWatch.Events;
using MotionWatch.Models;

namespace MotionWatch.Services;

public class GestosServiceFake : IGestosService
{
    private CancellationTokenSource? _cts;
    private readonly Random _rnd = new();

    public void Iniciar()
    {
        if (_cts != null) return;
        _cts = new CancellationTokenSource();
        var token = _cts.Token;

        WeakReferenceMessenger.Default.Send(new ConexionCambiadaMessage(true));

        _ = Task.Run(async () =>
        {
            var timer = new PeriodicTimer(TimeSpan.FromSeconds(1));
            try
            {
                while (await timer.WaitForNextTickAsync(token))
                {
                    var gesto = (Gesto)_rnd.Next(0, 4);
                    WeakReferenceMessenger.Default.Send(
                        new GestoDetectadoMessage(gesto, Math.Round(_rnd.NextDouble(), 2), DateTime.Now));
                    WeakReferenceMessenger.Default.Send(
                        new LatenciaMedidaMessage(_rnd.Next(15, 80)));
                }
            }
            catch (OperationCanceledException) { }
        }, token);
    }

    public void Detener()
    {
        _cts?.Cancel();
        _cts = null;
        WeakReferenceMessenger.Default.Send(new ConexionCambiadaMessage(false));
    }
}